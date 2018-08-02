package com.st.STM32WB.p2pDemo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.st.BlueSTSDK.gui.R;
import com.st.STM32WB.p2pDemo.feature.FeatureControlLed;
import com.st.STM32WB.p2pDemo.feature.FeatureNetworkStatus;
import com.st.STM32WB.p2pDemo.feature.FeatureSwitchStatus;
import com.st.BlueSTSDK.Feature;
import com.st.BlueSTSDK.Node;
import com.st.BlueSTSDK.gui.demos.DemoDescriptionAnnotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.st.STM32WB.p2pDemo.Peer2PeerDemoConfiguration.*;

@DemoDescriptionAnnotation(name="Multi Control",
        requareOneOf = {FeatureSwitchStatus.class,FeatureControlLed.class,FeatureNetworkStatus.class})
public class LedButtonNetworkControlFragment extends RssiDemoFragment {

    private static final String REMOVE_DEVICE_STATUS_KEY = LedButtonNetworkControlFragment.class.getCanonicalName()
            + ".REMOVE_DEVICE_STATUS_KEY";

    private FeatureSwitchStatus mButtonFeature;
    private FeatureControlLed mLedcontrolFeature;
    private Feature mEndDevMgtFeature;

    private Map<DeviceID,RemoteDeviceStatus> mStatus = new EnumMap<>(DeviceID.class);
    private STM32WBDeviceStatusRecyclerViewAdapter mStatusAdapter;
    private View mNodeListView;
    private View mScanInstructionView;

    //we cant initialize the listener here because we need to wait that the fragment is attached
    // to an activity
    private Feature.FeatureListener mButtonListener = new  Feature.FeatureListener () {

        @Override
        public void onUpdate(Feature f, Feature.Sample sample) {
            final DeviceID deviceId = FeatureSwitchStatus.getDeviceSelection(sample);
            final boolean isPressed = FeatureSwitchStatus.isSwitchOn(sample);

            if(mStatus.containsKey(deviceId)){
                mStatus.get(deviceId).buttonStatus =isPressed;
                updateGui(() -> mStatusAdapter.setDevices(mStatus.values()));
            }

        }//on update
    };

    //on update
    //we cant initialize the listener here becouse we need to wait that the fragment is attached
    // to an activity
    private Feature.FeatureListener mEndDevMgt = (f, sample) -> {

        for(DeviceID id : DeviceID.values()){
            if(FeatureNetworkStatus.isDeviceConnected(sample,id)){
                if(!mStatus.containsKey(id)){
                    mStatus.put(id,new RemoteDeviceStatus(id));
                }
            }else{
                if(mStatus.containsKey(id)){
                    mStatus.remove(id);
                }
            }
        }

        updateGui(()-> {
            if(mStatus.isEmpty()){
                showInstruction();
            }else {
                showDeviceStatus(mStatus.values());
            }
        });
    };

    private void showInstruction(){
        mScanInstructionView.setVisibility(View.VISIBLE);
        mNodeListView.setVisibility(View.GONE);
    }

    private void showDeviceStatus(Collection<RemoteDeviceStatus> statuses){
        mScanInstructionView.setVisibility(View.GONE);
        mNodeListView.setVisibility(View.VISIBLE);
        mStatusAdapter.setDevices(statuses);
    }

    public LedButtonNetworkControlFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_stm32wb_led_network_control, container, false);

        mStatusAdapter = new STM32WBDeviceStatusRecyclerViewAdapter((id, newStatus) -> {
            changeLedStatus(id,newStatus);
            mStatusAdapter.setDevices(mStatus.values());
        });
        RecyclerView recyclerView = root.findViewById(R.id.stm32wb_network_deviceList);
        recyclerView.setAdapter(mStatusAdapter);

        CompoundButton mControlAllLeds = root.findViewById(R.id.stm32wb_network_allLedSwitch);
        mControlAllLeds.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for(RemoteDeviceStatus status : mStatus.values()){
                changeLedStatus(status.id,isChecked);
            }
            mStatusAdapter.setDevices(mStatus.values());
        });

        mScanInstructionView = root.findViewById(R.id.stm32wb_network_instruciton);
        mNodeListView = root.findViewById(R.id.stm32wb_network_nodeListCard);

        if(savedInstanceState!=null){
            restoreDeviceStatus(savedInstanceState);
        }
        return root;
    }

    private void restoreDeviceStatus(@NonNull Bundle savedInstanceState){
        ArrayList<RemoteDeviceStatus> savedStatus = savedInstanceState.getParcelableArrayList(REMOVE_DEVICE_STATUS_KEY);
        if(savedStatus==null)
            return;
        //rebuild the map
        for(RemoteDeviceStatus status : savedStatus){
            mStatus.put(status.id,status);
        }
        showDeviceStatus(savedStatus);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<RemoteDeviceStatus> deviceStatuses = new ArrayList<>(mStatus.values());
        outState.putParcelableArrayList(REMOVE_DEVICE_STATUS_KEY,deviceStatuses);
    }

    private void changeLedStatus(DeviceID id, boolean newStatus){
        if(mLedcontrolFeature!=null){
            if(newStatus){
                mLedcontrolFeature.switchOnLed(id);
            }else{
                mLedcontrolFeature.switchOffLed(id);
            }
            mStatus.get(id).ledStatus=newStatus;
        }
    }

    @Override
    protected int getRssiLabelId() {
        return R.id.stm32wb_network_rssiText;
    }

    @Override
    protected void enableNeededNotification(@NonNull Node node) {
        super.enableNeededNotification(node);
        mButtonFeature = node.getFeature( FeatureSwitchStatus.class);
        mLedcontrolFeature = node.getFeature(FeatureControlLed.class);
        mEndDevMgtFeature = node.getFeature( FeatureNetworkStatus.class);

        mButtonFeature = node.getFeature(FeatureSwitchStatus.class);
        if (mButtonFeature != null) {
            mButtonFeature.addFeatureListener(mButtonListener);
            node.enableNotification(mButtonFeature);

        }
        mEndDevMgtFeature = node.getFeature(FeatureNetworkStatus.class);
        if (mEndDevMgtFeature != null) {
            mEndDevMgtFeature.addFeatureListener(mEndDevMgt);
            node.enableNotification(mEndDevMgtFeature);
            node.readFeature(mEndDevMgtFeature);
        }
    }

    @Override
    protected void disableNeedNotification(@NonNull Node node) {
        if(mButtonFeature!=null){
            mButtonFeature.removeFeatureListener(mButtonListener);
            node.disableNotification(mButtonFeature);
        }
        if(mEndDevMgtFeature != null) {
            mEndDevMgtFeature.removeFeatureListener(mEndDevMgt);
            node.disableNotification(mEndDevMgtFeature);
        }
        super.disableNeedNotification(node);
    }



    @Override
    public void onTxPowerChange(Node node, int newPower) {}




    public static class STM32WBDeviceStatusRecyclerViewAdapter extends
            RecyclerView.Adapter<STM32WBDeviceStatusRecyclerViewAdapter.ViewHolder>{

        private static final String LED_STATE_CHANGE = "LED_STATE_CHANGE";
        private static final String ALARM_STATE_CHANGE = "ALARM_STATE_CHANGE";

        interface LedStatusChangeListener {
            void onLedStatusChange(DeviceID id, boolean newStatus);
        }

        private List<RemoteDeviceStatus> devices=Collections.emptyList();
        private LedStatusChangeListener mListener;

        STM32WBDeviceStatusRecyclerViewAdapter(@NonNull LedStatusChangeListener listener){
            mListener = listener;
        }

        private List<RemoteDeviceStatus> copyDevicesStatus(Collection<RemoteDeviceStatus> devices){
            List<RemoteDeviceStatus> newDevices = new ArrayList<>(devices.size());
            for(RemoteDeviceStatus status : devices){
                newDevices.add(new RemoteDeviceStatus(status));
            }
            return newDevices;
        }

        public void setDevices(Collection<RemoteDeviceStatus> devices) {
            List<RemoteDeviceStatus> newDevices = copyDevicesStatus(devices);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DeviceStatusDiffCallback( newDevices,this.devices));
            this.devices = newDevices;
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stm32wb_remote, parent, false);
            return new ViewHolder(view,mListener);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RemoteDeviceStatus status = devices.get(position);
            holder.setStatus(status);
        }

        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if(payloads.isEmpty()){
                super.onBindViewHolder(holder,position,payloads);
                return;
            }
            Bundle changes = (Bundle) payloads.get(0);
            holder.applayChanges(changes);
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        private static class DeviceStatusDiffCallback extends DiffUtil.Callback{

            private List<RemoteDeviceStatus> oldDevices;
            private List<RemoteDeviceStatus> newDevices;

            DeviceStatusDiffCallback(List<RemoteDeviceStatus> newStatus, List<RemoteDeviceStatus> oldStatus) {
                this.newDevices = newStatus;
                this.oldDevices = oldStatus;
            }

            @Override
            public int getOldListSize() {
                return oldDevices.size();
            }

            @Override
            public int getNewListSize() {
                return newDevices.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldDevices.get(oldItemPosition).id == newDevices.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldDevices.get(oldItemPosition).equals(newDevices.get(newItemPosition));
            }

            @Nullable
            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                RemoteDeviceStatus oldStatus = oldDevices.get(oldItemPosition);
                RemoteDeviceStatus newStatus = newDevices.get(newItemPosition);
                Bundle changes = new Bundle();
                if(oldStatus.ledStatus != newStatus.ledStatus){
                    changes.putBoolean(LED_STATE_CHANGE,newStatus.ledStatus);
                }
                if(oldStatus.buttonStatus != newStatus.buttonStatus){
                    changes.putBoolean(ALARM_STATE_CHANGE,newStatus.buttonStatus);
                }
                return changes;
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder{

            private TextView mDeviceName;
            private ImageView mLedImage;
            private ImageView mAlarmImage;
            private CompoundButton mChangeLedStatus;
            private LedStatusChangeListener mLedStatusListener;
            private CompoundButton.OnCheckedChangeListener mCompoundButtonListener;

            public ViewHolder(View root,@NonNull LedStatusChangeListener listener) {
                super(root);

                mDeviceName = root.findViewById(R.id.stm32wb_remoteNodeText);
                mLedImage = root.findViewById(R.id.stm32wb_ledImage);
                mAlarmImage = root.findViewById(R.id.stm32wb_alarmImage);
                mChangeLedStatus = root.findViewById(R.id.stm32wb_ledSwitch);
                mLedStatusListener = listener;

            }


            private void setLedStatus(boolean isOn){
                if(isOn){
                    mLedImage.setImageResource(R.drawable.stm32wb_led_on);
                }else{
                    mLedImage.setImageResource(R.drawable.stm32wb_led_off);
                }
                //update the switch without propagate the change to the user
                mChangeLedStatus.setOnCheckedChangeListener(null);
                mChangeLedStatus.setChecked(isOn);
                mChangeLedStatus.setOnCheckedChangeListener(mCompoundButtonListener);
            }

            private void setAlarmState(boolean isOn){
                if(isOn){
                    mAlarmImage.setVisibility(View.VISIBLE);
                }else{
                    mAlarmImage.setVisibility(View.INVISIBLE);
                }
            }

            void setStatus(RemoteDeviceStatus status){
                setLedStatus(status.ledStatus);
                setAlarmState(status.buttonStatus);

                Context ctx = mDeviceName.getContext();
                mDeviceName.setText(ctx.getString(R.string.stm32wb_deviceNameFormat,status.id.getId()));

                mChangeLedStatus.setChecked(status.ledStatus);
                mCompoundButtonListener = (buttonView, isChecked) ->
                        mLedStatusListener.onLedStatusChange(status.id,isChecked);
                mChangeLedStatus.setOnCheckedChangeListener(mCompoundButtonListener);
            }

            void applayChanges(Bundle changes) {
                if(changes.containsKey(LED_STATE_CHANGE)){
                    setLedStatus(changes.getBoolean(LED_STATE_CHANGE));
                }
                if(changes.containsKey(ALARM_STATE_CHANGE)){
                    setAlarmState(changes.getBoolean(ALARM_STATE_CHANGE));
                }
            }
        }

    }
}
