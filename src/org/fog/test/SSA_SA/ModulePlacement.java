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
public class ModulePlacement extends Placement {
    /**
     * 虚拟机与设备之间的映射关系
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
     * 放置的模块（虚拟机）
     */
    protected String moduleToPlace;
    /**
     * 每个fogDevice 当前的MIPS信息
     */
    protected Map<Integer, Integer> deviceMipsInfo;

    public ModulePlacement(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
                           Application application, ModuleMapping moduleMapping, String moduleToPlace) {
        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.setModuleMapping(moduleMapping);
        this.setModuleToDeviceMap(new HashMap<>());
        this.setDeviceToModuleMap(new HashMap<>());
        setSensors(sensors);
        setActuators(actuators);
        this.moduleToPlace = moduleToPlace;
        this.deviceMipsInfo = new HashMap<>();
        //重要的是这步 分配虚拟机
        mapModules();
    }

    public ModulePlacement(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
                           Application application, ModuleMapping moduleMapping) {
        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.setModuleMapping(moduleMapping);
        this.setModuleToDeviceMap(new HashMap<>());
        this.setDeviceToModuleMap(new HashMap<>());
        setSensors(sensors);
        setActuators(actuators);
        this.deviceMipsInfo = new HashMap<>();
        //重要的是这步 分配虚拟机
        mapModules();
    }



    @Override
    protected void mapModules() {
        // 循环遍历 moduleMapping  moduleMapping 是 一个设备 对应 多个虚拟机的关系
        for (String deviceName : getModuleMapping().getModuleMapping().keySet()) {
            for (String moduleName : getModuleMapping().getModuleMapping().get(deviceName)) {
                //拿到每个设备的ID  开始看到这你可能会奇怪 我在 new FogDevice() 里 也没有设ID，其实你想设ID也不行 他压根就没提供 带ID的构造器
                //言归正传，他这个ID是在 new FogDevice() 时一层一层初始化父类 直到 SimEntity 的构造方法 这里面有个 CloudSim.addEntity(this) 方法，在这里初始化了ID
                int deviceId = CloudSim.getEntityId(deviceName);
                //根据虚拟机名字 拿到 虚拟机实体
                AppModule appModule = getApplication().getModuleByName(moduleName);
                List<AppModule> placesModules;
                if (!getDeviceToModuleMap().containsKey(deviceId)) {
                    placesModules = new ArrayList<>();
                } else {
                    placesModules = getDeviceToModuleMap().get(deviceId);
                }
                //这里是真正的设备与虚拟机分配关系 使用 deviceToModuleMap属性 来保存
                placesModules.add(appModule);
                getDeviceToModuleMap().put(deviceId, placesModules);
            }
        }

        //以下是自定义配置  其实这里 怎么写 即怎么分配虚拟机 都可以，你可以像上面代码 根据 之前保存的moduleMapping 来分配虚拟机 也可以不按
        //他这里的分配规则 是把层级为 1 的设备 的子设备 按照deadline 排序 根据优先级分 且还按照 mips 来分的。他这里的deadline 是之前在 createApplication 方法里加的
//        for (FogDevice device : getFogDevices()) {
//            int deviceParent = -1;
//
//            List<Integer> children = new ArrayList<>();
//
//            //这里保存层级为 1 的设备的 mips 为 0  暂且不知有什么用
//            if (device.getLevel() == 1) {
//                if (!deviceMipsInfo.containsKey(device.getId())) {
//                    deviceMipsInfo.put(device.getId(), 0);
//                }
//                deviceParent = device.getParentId();
//
//                //获取层级为 1 的设备的孩子节点
//                for (FogDevice deviceChild : getFogDevices()) {
//                    if (deviceChild.getParentId() == device.getId()) {
//                        children.add(deviceChild.getId());
//                    }
//                }
//
//                Map<Integer, Double> childDeadLine = new HashMap<>();
//                //获取子设备的deadline
//                for (int childId : children) {
//                    childDeadLine.put(childId, getApplication().getDeadlineInfo().get(childId).get(moduleToPlace));
//                }
//                //子设备的id
//                List<Integer> keys = new ArrayList<>(childDeadLine.keySet());
//                //根据deadline 排序
//                for (int i = 0; i < keys.size() - 1; i++) {
//                    for (int j = 0; j < keys.size() - i - 1; j++) {
//                        if (childDeadLine.get(keys.get(j)) > childDeadLine.get(keys.get(j + 1))) {
//                            int tempJn = keys.get(j + 1);
//                            keys.set(j, tempJn);
//                            keys.set(j + 1, tempJn);
//                        }
//                    }
//                }
//
//                //根据 mips 做了一些 自定义配置（分配虚拟机）
//                //这里根据 虚拟机名字 拿到 new虚拟机时 传入的 mips
//                int baseMipsOfPlacingModule = (int) getApplication().getModuleByName(moduleToPlace).getMips();
//                for (int key : keys) {
//                    int currentMips = deviceMipsInfo.get(device.getId());
//                    AppModule appModule = getApplication().getModuleByName(moduleToPlace);
//                    int additionalMips = getApplication().getAdditionalMipsInfo().get(key).get(moduleToPlace);
//                    if (currentMips + baseMipsOfPlacingModule + additionalMips < device.getMips()) {
//                        currentMips = currentMips + baseMipsOfPlacingModule + additionalMips;
//                        deviceMipsInfo.put(device.getId(), currentMips);
//                        List<AppModule> placedModules;
//                        //将module 放置在这个fogDevice上
//                        if (!getDeviceToModuleMap().containsKey(device.getId())) {
//                            placedModules = new ArrayList<AppModule>();
//
//                        } else {
//                            placedModules = getDeviceToModuleMap().get(device.getId());
//                        }
//                        placedModules.add(appModule);
//                        getDeviceToModuleMap().put(device.getId(), placedModules);
//
//
//                    } else {
//                        List<AppModule> placesModules = getDeviceToModuleMap().get(deviceParent);
//                        placesModules.add(appModule);
//                        getDeviceToModuleMap().put(deviceParent, placesModules);
//                    }
//                }
//            }
//        }
    }
}
