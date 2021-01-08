package org.fog.test.SSA_SA;

import lombok.Getter;
import lombok.Setter;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.placement.ModuleMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Tiger
 * @date 2021/1/8 10:37
 */
@Getter
@Setter
public  class ModulePlacement extends Placement {
    /**
     * 模块设备的对应
     */
    protected ModuleMapping moduleMapping;
    /**
     * 监听器
     */
    protected List<Sensor> sensors;
    /**
     * 执行器
     */
    protected List<Actuator> actuators;
    /**
     * 放置的模块
     */
    protected String moduleToPlace;
    /**
     * 每个fogDevice 当前的MIPS信息
     */
    protected Map<Integer, Integer> deviceMipsInfo;

    public ModulePlacement(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
                           Application application, ModuleMapping moduleMapping, String moduleToPlace){
        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.setModuleMapping(moduleMapping);
        this.setModuleToDeviceMap(new HashMap<>());
        this.setDeviceToModuleMap(new HashMap<>());
        setSensors(sensors);
        setActuators(actuators);
        this.moduleToPlace = moduleToPlace;
        this.deviceMipsInfo = new HashMap<>();
        mapModules();
    }

    @Override
    protected void mapModules() {
        //根据设置的对应情况，初始化设备和module对应map
        for (String deviceName:getModuleMapping().getModuleMapping().keySet()){
            for (String moduleName:getModuleMapping().getModuleMapping().get(deviceName)){
                int deviceId= CloudSim.getEntityId(deviceName);
                AppModule appModule=getApplication().getModuleByName(moduleName);
                if (!getDeviceToModuleMap().containsKey(deviceId)){
                    List<AppModule> placesModules=new ArrayList<>();
                    placesModules.add(appModule);
                    getDeviceToModuleMap().put(deviceId,placesModules);
                }
                else {
                    List<AppModule> placesModules=getDeviceToModuleMap().get(deviceId);
                    placesModules.add(appModule);
                    getDeviceToModuleMap().put(deviceId,placesModules);
                }
            }
        }

        for (FogDevice device:getFogDevices()){
            int deviceParent=-1;
            List<Integer> children=new ArrayList<>();


            if (device.getLevel()==1){
                if (!deviceMipsInfo.containsKey(device.getId())){
                    deviceMipsInfo.put(device.getId(),0);
                }
                deviceParent=device.getParentId();

                //获取该设备的孩子节点
                for (FogDevice deviceChild:getFogDevices()){
                    if (deviceChild.getParentId()==device.getId()){
                        children.add(deviceChild.getId());
                    }
                }

                Map<Integer,Double>childDeadLine=new HashMap<>();
                //获取子设备的deadline
                for (int childId:children){
                    childDeadLine.put(childId,getApplication().getDeadlineInfo().get(childId).get(moduleToPlace));
                }
                //子设备的id
                List<Integer> keys=new ArrayList<>(childDeadLine.keySet());
                //根据deadline 排序
                for (int i=0;i<keys.size()-1;i++){
                    for (int j=0;j<keys.size()-i-1;j++){
                        if (childDeadLine.get(keys.get(j))>childDeadLine.get(keys.get(j+1))){
                            int tmepJ=keys.get(j);
                            int tempJn=keys.get(j+1);
                            keys.set(j,tempJn);
                            keys.set(j+1,tempJn);
                        }
                    }
                }


                int baseMipsOfPlacingModule=(int) getApplication().getModuleByName(moduleToPlace).getMips();
                for (int key:keys){
                    int currentMips=deviceMipsInfo.get(device.getId());
                    AppModule appModule=getApplication().getModuleByName(moduleToPlace);
                    int additionalMips=getApplication().getAdditionalMipsInfo().get(key).get(moduleToPlace);
                    if (currentMips+baseMipsOfPlacingModule+additionalMips<device.getMips()){
                        currentMips=currentMips+baseMipsOfPlacingModule+additionalMips;
                        deviceMipsInfo.put(device.getId(),currentMips);
                        List<AppModule> placedModules;
                        //将module 放置在这个fogDevice上
                        if(!getDeviceToModuleMap().containsKey(device.getId()))
                        {
                            placedModules = new ArrayList<AppModule>();

                        }
                        else
                        {
                            placedModules = getDeviceToModuleMap().get(device.getId());
                        }
                        placedModules.add(appModule);
                        getDeviceToModuleMap().put(device.getId(), placedModules);


                    }else{
                        List<AppModule> placesModules=getDeviceToModuleMap().get(deviceParent);
                        placesModules.add(appModule);
                        getDeviceToModuleMap().put(deviceParent,placesModules);
                    }
                }
            }
        }
    }
}
