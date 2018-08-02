package com.st.STM32WB.fwUpgrade;

import android.support.annotation.NonNull;
import android.util.Log;

import com.st.BlueSTSDK.Feature;
import com.st.BlueSTSDK.Node;
import com.st.BlueSTSDK.gui.fwUpgrade.FirmwareType;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.FwUpgradeConsole;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.util.FwFileDescriptor;
import com.st.STM32WB.fwUpgrade.feature.OTABoardWillRebootFeature;
import com.st.STM32WB.fwUpgrade.feature.OTAControlFeature;
import com.st.STM32WB.fwUpgrade.feature.OTAFileUpload;

import java.io.FileNotFoundException;
import java.io.IOException;

public class FwUpgradeConsoleSTM32WB extends FwUpgradeConsole {


    public static FwUpgradeConsoleSTM32WB buildForNode(Node node){
        OTAControlFeature control = node.getFeature(OTAControlFeature.class);
        OTAFileUpload upload = node.getFeature(OTAFileUpload.class);
        OTABoardWillRebootFeature reboot = node.getFeature(OTABoardWillRebootFeature.class);
        if(control!=null && upload!=null && reboot!=null){
            return new FwUpgradeConsoleSTM32WB(control,upload,reboot);
        }else{
            return null;
        }
    }

    private OTAControlFeature mControl;
    private OTAFileUpload mUpload;
    private OTABoardWillRebootFeature mReset;

    private FwUpgradeConsoleSTM32WB(@NonNull OTAControlFeature control,
                                    @NonNull OTAFileUpload upload,
                                    @NonNull OTABoardWillRebootFeature reset){
        super(null);
        mControl = control;
        mUpload = upload;
        mReset = reset;
    }

    @Override
    public boolean loadFw(@FirmwareType int type, FwFileDescriptor fwFile,long startAddress) {
        Feature.FeatureListener onBoardWillReboot = (f, sample) -> {
            if(OTABoardWillRebootFeature.boardIsRebooting(sample))
                mCallback.onLoadFwComplete(FwUpgradeConsoleSTM32WB.this,fwFile);
            else
                mCallback.onLoadFwError(FwUpgradeConsoleSTM32WB.this,fwFile, FwUpgradeCallback.ERROR_TRANSMISSION);
            mReset.getParentNode().disableNotification(mReset);
        };
        mReset.addFeatureListener(onBoardWillReboot);
        mReset.getParentNode().enableNotification(mReset);
        mControl.startUpload(type,startAddress);
        try {

            Runnable onProgress = new Runnable() {
                    private long sendData = fwFile.getLength();
                    @Override
                    public void run() {
                        sendData-=OTAFileUpload.CHUNK_LENGTH;
                        Log.d("OnProgress", "run: "+sendData);
                        mCallback.onLoadFwProgressUpdate(FwUpgradeConsoleSTM32WB.this,fwFile,sendData);
                        if(sendData<=0){
                            mControl.uploadFinished(() -> {
                            //    mCallback.onLoadFwComplete(FwUpgradeConsoleSTM32WB.this,fwFile);
                            //    mReset.removeFeatureListener(onBoardWillReboot);
                            });
                        }
                    }
            };

            mUpload.upload(fwFile.openFile(),onProgress);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mControl.cancelUpload();
            mCallback.onLoadFwError(this,fwFile,FwUpgradeCallback.ERROR_INVALID_FW_FILE);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            mControl.cancelUpload();
            mCallback.onLoadFwError(this,fwFile,FwUpgradeCallback.ERROR_TRANSMISSION);
            return false;
        }
        return true;
    }
}