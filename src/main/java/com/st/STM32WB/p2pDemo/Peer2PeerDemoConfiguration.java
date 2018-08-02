package com.st.STM32WB.p2pDemo;

import android.support.annotation.NonNull;

import com.st.BlueSTSDK.Node;
import com.st.BlueSTSDK.Utils.UUIDToFeatureMap;
import com.st.STM32WB.p2pDemo.feature.FeatureControlLed;
import com.st.STM32WB.p2pDemo.feature.FeatureNetworkStatus;
import com.st.STM32WB.p2pDemo.feature.FeatureSwitchStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Class containing the settings and common structure for the Peer2Peer (P2P) stm32wb demo
 */
public class Peer2PeerDemoConfiguration {

    private static final Map<Byte,DeviceID> BOARDID_TO_DEVICEID;


     static {
        BOARDID_TO_DEVICEID = new HashMap<>();
        BOARDID_TO_DEVICEID.put((byte)0x83,DeviceID.DEVICE_1);
        BOARDID_TO_DEVICEID.put((byte)0x84,DeviceID.DEVICE_2);
        BOARDID_TO_DEVICEID.put((byte)0x87,DeviceID.DEVICE_3);
        BOARDID_TO_DEVICEID.put((byte)0x88,DeviceID.DEVICE_4);
        BOARDID_TO_DEVICEID.put((byte)0x89,DeviceID.DEVICE_5);
        BOARDID_TO_DEVICEID.put((byte)0x8A,DeviceID.DEVICE_6);
    }

    /**
     * id used for the router node
     */
    public static final Set<Byte> WB_DEVICE_NODE_IDS =BOARDID_TO_DEVICEID.keySet();

    /**
     * id used for the router node
     */
    public static final byte WB_ROUTER_NODE_ID =(byte)0x85;

    /**
     * tell if the node is a valid node for the P2P demo
     * @param node node to test
     * @return true if the node is manage by this demo
     */
    public static boolean isValidNode(@NonNull Node node){
        return isValidDeviceNode(node) || isValidRouterNode(node);
    }

    public static boolean isValidDeviceNode(@NonNull Node node){
        byte nodeId = node.getTypeId();
        return node.getType() == Node.Type.NUCLEO &&
                (WB_DEVICE_NODE_IDS.contains(nodeId));
    }

    public static boolean isValidRouterNode(@NonNull Node node){
        byte nodeId = node.getTypeId();
        return node.getType() == Node.Type.NUCLEO &&
                (WB_ROUTER_NODE_ID == nodeId);
    }

    /**
     * map the characteristics and the feature used by this demo
     * @return map containing the characteristics and feature used by this demo
     */
    public static UUIDToFeatureMap getCharacteristicMapping(){
        UUIDToFeatureMap temp = new UUIDToFeatureMap();
        temp.put(UUID.fromString("0000fe41-8e22-4541-9d4c-21edae82ed19"), FeatureControlLed.class);
        temp.put(UUID.fromString("0000fe42-8e22-4541-9d4c-21edae82ed19"), FeatureSwitchStatus.class);
        temp.put(UUID.fromString("0000fe51-8e22-4541-9d4c-21edae82ed19"), FeatureNetworkStatus.class);
        return temp;
    }

    /**
     * enum containing the different device id
     */
    public enum DeviceID {

        /** The End Device 1 */
        DEVICE_1((byte) 0x01),
        /** The End Device 2 */
        DEVICE_2((byte) 0x02),
        /** The End Device 3 */
        DEVICE_3((byte) 0x03),
        /** The End Device 4 */
        DEVICE_4((byte) 0x04),
        /** The End Device 5 */
        DEVICE_5((byte) 0x05),
        /** The End Device 6 */
        DEVICE_6((byte) 0x06),
        /** all the devices  */
        ALL((byte) 0x00),
        /** Invalid value */
        UNKNOWN((byte) 0xFF);

        private byte deviceId;

        DeviceID(byte id){
            deviceId = id;
        }

        public byte getId() {
            return deviceId;
        }

        public static DeviceID fromBoardId(byte id){
            return BOARDID_TO_DEVICEID.get(id);
        }

    }//DeviceSelection

}