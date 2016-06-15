package com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.st.BlueSTSDK.Debug;
import com.st.BlueSTSDK.Utils.NumberConversion;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.util.IllegalVersionFormatException;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.util.ImgFileInputStream;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.util.STM32Crc32;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.Checksum;

/**
 * Implement the FwUpgradeConsole for a board running the BlueMs firmware.
 * In this case the protocol is:
 * mobile:upgrade[Ble|Fw]+length+fileCrc
 * node:fileCrc
 * mobile: file data, the file is spited in message of 16bytes
 * node: when all the byte are write return 1 if the crc is ok, -1 otherwise
 */
public class FwUpgradeConsoleNucleo extends FwUpgradeConsole {

    static private final String GET_VERSION_BOARD_FW="versionFw\n";
    static private final String GET_VERSION_BLE_FW="versionBle\n";
    static private final byte[] UPLOAD_BOARD_FW={'u','p','g','r','a','d','e','F','w'};
    static private final byte[] UPLOAD_BLE_FW={'u','p','g','r','a','d','e','B','l','e'};

    static private final String ACK_MSG="\u0001";

    /**
     * the Stm32 L4 can write only 8bytes at time, so sending a multiple of 8 simplify the fw code
     */
    static private final int MAX_MSG_SIZE=16;

    /**
     * if all the messages are not send in 1s an error is fired
     */
    static private final int LOST_MSG_TIMEOUT_MS=1000;

    /**
     * object that will receive the console data
     */
    private Debug.DebugOutputListener mCurrentListener;

    /**
     * Buffer where store the command response
     */
    private StringBuilder mBuffer;

    /**
     * object used for manage the get board id command
     */
    private GetVersionProtocol mConsoleGetFwVersion= new GetVersionProtocol();

    /**
     * class used for wait/parse the fw version response
     */
    private class GetVersionProtocol implements Debug.DebugOutputListener {

        private @FirmwareType int mRequestFwType;

        public void requestVersion(@FirmwareType int fwType){
            mRequestFwType=fwType;
            switch (fwType) {
                case FwUpgradeConsole.BLE_FW:
                    mConsole.write(GET_VERSION_BLE_FW);
                    break;
                case FwUpgradeConsole.BOARD_FW:
                    mConsole.write(GET_VERSION_BOARD_FW);
                    break;
                default:
                    if(mCallback!=null){
                        mCallback.onVersionRead(FwUpgradeConsoleNucleo.this,fwType,null);
                    }
                    break;
            }
        }

        @Override
        public void onStdOutReceived(Debug debug, String message) {
            mBuffer.append(message);
            if (mBuffer.length()>2 &&
                    mBuffer.substring(mBuffer.length()-2).equals("\r\n")) {
                mBuffer.delete(mBuffer.length()-2,mBuffer.length());
                FwVersion version=null;
                try {
                    switch (mRequestFwType) {
                        case FwUpgradeConsole.BLE_FW:
                            version = new FwVersionBle(mBuffer.toString());
                            break;
                        case FwUpgradeConsole.BOARD_FW:
                            version = new FwVersionBoard(mBuffer.toString());
                            break;
                    }
                }catch (IllegalVersionFormatException e){
                    //clear the current buffer and wait for a new answer
                    mBuffer.delete(0,mBuffer.length());
                    return;
                }
                setConsoleListener(null);
                if (mCallback != null)
                    mCallback.onVersionRead(FwUpgradeConsoleNucleo.this,mRequestFwType,version);
            }
        }

        @Override
        public void onStdErrReceived(Debug debug, String message) { }

        @Override
        public void onStdInSent(Debug debug, String message, boolean writeResult) { }
    }


    /**
     * class that manage the file upload
     */
    private class UploadFileProtocol implements  Debug.DebugOutputListener{

        /**
         * for not stress the ble stack it sends 10 package at times, when the packages are sent
         * it send a new set a package
         */
        private static final int BLOCK_PKG_SIZE = 10;

        /**
         * file that we are uploading
         */
        private Uri mFile;

        /**
         * buffer where we are reading the file
         */
        private InputStream mFileData;

        /**
         * number of byte send to the node
         */
        private long mByteSend;

        /**
         * total number of byte to send
         */
        private long mByteToSend;

        /**
         * file crc
         */
        private long mCrc;

        /**
         * true if the handshake with the node is finished
         */
        private boolean mNodeReadyToReceiveFile;

        /**
         * counter of package that are sent
         */
        private int mNPackageReceived;

        /**
         * size of the last package send
         */
        private byte[] mLastPackageSend = new byte[MAX_MSG_SIZE];

        /**
         * if the timeout is rise, fire an error of type
         * {@link FwUpgradeConsole.FwUpgradeCallback#ERROR_TRANSMISSION}
         */
        private Runnable onTimeout = new Runnable() {
            @Override
            public void run() {
                onLoadFail(FwUpgradeCallback.ERROR_TRANSMISSION);
            }
        };


        /**
         * Notify to the used that an error happen
         * @param errorCode type of error
         */
        private void onLoadFail(@FwUpgradeCallback.UpgradeErrorType int errorCode){
            if(mCallback!=null)
                mCallback.onLoadFwError(FwUpgradeConsoleNucleo.this,mFile,errorCode);
            setConsoleListener(null);
        }

        /**
         * notify to the user that the upload is correctly finished
         */
        private void onLoadComplete(){
            if(mCallback!=null)
                mCallback.onLoadFwComplete(FwUpgradeConsoleNucleo.this,mFile);
            setConsoleListener(null);
        }

        /**
         * open a file, using the right InputStream
         * @param f file to open, if it is of type img the {@link ImgFileInputStream} class is
         *          used, otherwise a {@link FileInputStream} is used
         * @throws FileNotFoundException if is not possible read the file
         */
        private void openFile(File f) throws FileNotFoundException {
            if(f.getName().toLowerCase().endsWith("img")) {
                ImgFileInputStream input = new ImgFileInputStream(f);
                mFileData =input;
                mByteToSend = input.length();
            }else {
                mFileData = new FileInputStream(f);
                mByteToSend = f.length();
            }//if-else
        }

        /**
         * compute the file crc
         * @param f file to open
         * @return file crc or a negative number if an error happen
         */
        private long computeCrc32(File f){
            Checksum crc = new STM32Crc32();
            byte buffer[] = new byte[4];
            try {
                openFile(f);
                //the file must be multiple of 32bit,
                long fileSize = mByteToSend - mByteToSend%4;
                BufferedInputStream inputStream = new BufferedInputStream(mFileData);
                for(long i=0;i<fileSize;i+=4){
                    if(inputStream.read(buffer)==buffer.length)
                        crc.update(buffer,0,buffer.length);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
            return crc.getValue();
        }

        /**
         * merge the file size and crc for create the command that will start the upload on the
         * board
         * @param fwType firmware to update
         * @param fileSize number of file to send
         * @param fileCrc file crc
         * @return command to send to the board
         */
        private byte[] prepareLoadCommand(@FirmwareType int fwType,long fileSize,long
                fileCrc){
            byte[] command;
            int offset;
            if(fwType==BLE_FW){
                offset = UPLOAD_BLE_FW.length;
                command =new byte[offset+8];
                System.arraycopy(UPLOAD_BLE_FW,0,command,0,offset);
            }else{
                offset = UPLOAD_BOARD_FW.length;
                command =new byte[offset+8];
                System.arraycopy(UPLOAD_BOARD_FW,0,command,0,offset);
            }
            byte temp[] = NumberConversion.LittleEndian.uint32ToBytes(fileSize);
            System.arraycopy(temp,0,command,offset,temp.length);
            offset+=temp.length;
            temp = NumberConversion.LittleEndian.uint32ToBytes(fileCrc);
            System.arraycopy(temp,0,command,offset,temp.length);

            return command;
        }

        /**
         * start to upload the file
         * @param fwType firmware that we are uploading
         * @param file file to upload
         */
        public void loadFile(@FirmwareType int fwType,Uri file){
            mFile=file;
            File f = new File(file.getPath());
            mCrc = computeCrc32(f);
            try {
                openFile(f);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                onLoadFail(FwUpgradeCallback.ERROR_INVALID_FW_FILE);
                return;
            }
            //send the start command

            mNodeReadyToReceiveFile =false;
            mConsole.write(prepareLoadCommand(fwType,mByteToSend,mCrc));
        }

        /**
         * @param message message received from the node
         * @return true if the message contain the crc code that we have send
         */
        private boolean checkCrc(String message){
            byte rcvCrc[] = message.getBytes(Charset.forName("ISO-8859-1"));
            byte myCrc[] = NumberConversion.LittleEndian.uint32ToBytes(mCrc);
            return Arrays.equals(rcvCrc,myCrc);
        }

        /**
         * read the data from the file and send it to the node
         * @return true if the package is correctly sent
         */
        private boolean sendFwPackage(){
            int lastPackageSize = (int) Math.min(mByteToSend - mByteSend, MAX_MSG_SIZE);
            int byteRead;
            try {
                byteRead = mFileData.read(mLastPackageSend, 0, lastPackageSize);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            if (lastPackageSize == byteRead) {
                mByteSend += byteRead;
                return mConsole.write(mLastPackageSend, 0, lastPackageSize)==lastPackageSize;
            } else
                //it read an unaspected number of byte, something bad happen
                return false;
        }//sendFwPackage

        /**
         * send a block of message, the function will stop at the first error
         * @return true if all the message are send correctly
         */
        private boolean sendPackageBlock(){
            for(int i = 0; i< BLOCK_PKG_SIZE; i++){
                if(!sendFwPackage())
                    return false;
            }//for
            return true;
        }//sendPackageBlock


        @Override
        public void onStdOutReceived(Debug debug, String message) {
            if(!mNodeReadyToReceiveFile){
                if(checkCrc(message)) {
                    mNodeReadyToReceiveFile = true;
                    mNPackageReceived=0;
                    sendPackageBlock();
                }else
                    onLoadFail(FwUpgradeCallback.ERROR_TRANSMISSION);
            }else { //transfer complete
                mTimeout.removeCallbacks(onTimeout);
                if(message.equalsIgnoreCase(ACK_MSG))
                    onLoadComplete();
                else
                    onLoadFail(FwUpgradeCallback.ERROR_CORRUPTED_FILE);
            }
        }//onStdOutReceived

        /**
         * notify to the user that a block of data is correctly send and send a new one
         */
        private void notifyNodeReceivedFwMessage(){
            mNPackageReceived++;
            //if we finish to send all the message
            if(mNPackageReceived % BLOCK_PKG_SIZE ==0){
                if(mCallback!=null)
                    mCallback.onLoadFwProgressUpdate(FwUpgradeConsoleNucleo.this,mFile,
                            mByteToSend-mByteSend);
                sendPackageBlock();
            }//if
        }

        @Override
        public void onStdInSent(Debug debug, String message, boolean writeResult) {
            if(writeResult){
                if(mNodeReadyToReceiveFile){
                    //reset the timeout
                    mTimeout.removeCallbacks(onTimeout);
                    notifyNodeReceivedFwMessage();
                    mTimeout.postDelayed(onTimeout,LOST_MSG_TIMEOUT_MS);
                }
            }else{
                onLoadFail(FwUpgradeCallback.ERROR_TRANSMISSION);
            }
        }

        @Override
        public void onStdErrReceived(Debug debug, String message) { }
    }

    /**
     * object used for manage the get board id command
     */
    private UploadFileProtocol mConsoleUpgradeFw = new UploadFileProtocol();

    /**
     * handler used for the command timeout
     */
    private Handler mTimeout;

    /**
     * build a debug console without a callback
     * @param console console to use for send the command
     */
    FwUpgradeConsoleNucleo(Debug console){
        this(console,null);
    }

    /**
     *
     * @param console console where send the command
     * @param callback object where notify the command answer
     */
    FwUpgradeConsoleNucleo(Debug console, FwUpgradeConsole.FwUpgradeCallback callback) {
        super(console,callback);
        mTimeout = new Handler(Looper.getMainLooper());
        mBuffer = new StringBuilder();

    }

    /**
     * change the listener to use for receive the debug console message, null will close the
     * debug console
     *
     * @param listener object to use for notify the console messages
     */
    private void setConsoleListener(Debug.DebugOutputListener listener) {
        synchronized (this) {
            mCurrentListener = listener;
            mConsole.setDebugOutputListener(listener);
        }//synchronized
    }

    @Override
    public boolean isWaitingAnswer() {
        return mCurrentListener != null;
    }

    @Override
    public boolean readVersion(@FirmwareType int fwType) {
        if (isWaitingAnswer())
            return false;

        mBuffer.setLength(0); //reset the buffer
        setConsoleListener(mConsoleGetFwVersion);
        mConsoleGetFwVersion.requestVersion(fwType);
        return true;
    }

    @Override
    public boolean loadFw(@FirmwareType int fwType,final Uri fwFile) {
        if (isWaitingAnswer())
            return false;

        mBuffer.setLength(0); //reset the buffer

        setConsoleListener(mConsoleUpgradeFw);
        mConsoleUpgradeFw.loadFile(fwType,fwFile);
        return  true;
    }
}
