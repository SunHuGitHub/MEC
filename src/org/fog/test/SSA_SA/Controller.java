package org.fog.test.SSA_SA;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.utils.*;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Controller extends SimEntity {

    public static boolean ONLY_CLOUD = false;
    /**
     * 所有设备集合
     */
    private List<FogDevice> fogDevices;
    /**
     * 传感器-主要发任务用的
     */
    private List<Sensor> sensors;
    /**
     * 执行器
     */
    private List<Actuator> actuators;
    /**
     * 应用集合--逻辑架构图
     */
    private Map<String, Application> applications;
    /**
     * 加载延迟集合
     */
    private Map<String, Integer> appLaunchDelays;
    /**
     * 放置策略集合
     */
    private Map<String, ModulePlacement> appModulePlacementPolicy;

    public Controller(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators) {
        super(name);
        this.applications = new HashMap<String, Application>();
        setAppLaunchDelays(new HashMap<String, Integer>());
        setAppModulePlacementPolicy(new HashMap<String, ModulePlacement>());
        //每个 设备 保存 Controller ID
        for (FogDevice fogDevice : fogDevices) {
            fogDevice.setControllerId(getId());
        }
        setFogDevices(fogDevices);
        setActuators(actuators);
        setSensors(sensors);
        //初始化父子设备的关系 里面使用了两个map  一个保存子设备的ID 一个保存子设备的延迟
        connectWithLatencies();
    }

    private FogDevice getFogDeviceById(int id) {
        for (FogDevice fogDevice : getFogDevices()) {
            if (id == fogDevice.getId())
                return fogDevice;
        }
        return null;
    }

    private void connectWithLatencies() {
        for (FogDevice fogDevice : getFogDevices()) {
            //拿到父节点
            FogDevice parent = getFogDeviceById(fogDevice.getParentId());
            if (parent == null)
                continue;
            double latency = fogDevice.getUplinkLatency();
            //父节点保存子设备延迟
            parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
            //父节点保存子设备ID
            parent.getChildrenIds().add(fogDevice.getId());
        }
    }

    @Override
    public void startEntity() {
        //咱们只有一个Application
        for (String appId : applications.keySet()) {
            if (getAppLaunchDelays().get(appId) == 0)
                //方法里发出 设备激活 虚拟机激活事件
                processAppSubmit(applications.get(appId));
            else
                send(getId(), getAppLaunchDelays().get(appId), FogEvents.APP_SUBMIT, applications.get(appId));
        }
        //controller资源管理事件
        send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
        //结束模拟事件
        send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);

        //FogDevice资源管理事件
        for (FogDevice dev : getFogDevices())
            sendNow(dev.getId(), FogEvents.RESOURCE_MGMT);

    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.APP_SUBMIT:
                processAppSubmit(ev);
                break;
            case FogEvents.TUPLE_FINISHED:
                //空方法
                processTupleFinished(ev);
                break;
            case FogEvents.CONTROLLER_RESOURCE_MANAGE:
                //发出资源更新事件
                manageResources();
                break;
            case FogEvents.STOP_SIMULATION:
                CloudSim.stopSimulation();
                //打印时间统计
                printTimeDetails();
                //打印能耗统计
                printPowerDetails();
                //打印费用统计
                printCostDetails();
                //打印网络资源统计
                printNetworkUsageDetails();
                System.exit(0);
                break;

        }
    }

    private void printNetworkUsageDetails() {
        System.out.println("Total network usage = " + NetworkUsageMonitor.getNetworkUsage() / Config.MAX_SIMULATION_TIME);
    }

    private FogDevice getCloud() {
        for (FogDevice dev : getFogDevices())
            if (dev.getName().equals("mecServer"))
                return dev;
        return null;
    }

    private void printCostDetails() {
        System.out.println("Cost of execution in cloud = " + getCloud().getTotalCost());
    }

    private void printPowerDetails() {
        for (FogDevice fogDevice : getFogDevices()) {
            System.out.println(fogDevice.getName() + " : Energy Consumed = " + fogDevice.getEnergyConsumption());
        }
    }

    private String getStringForLoopId(int loopId) {
        for (String appId : getApplications().keySet()) {
            Application app = getApplications().get(appId);
            for (AppLoop loop : app.getLoops()) {
                if (loop.getLoopId() == loopId)
                    return loop.getModules().toString();
            }
        }
        return null;
    }

    private void printTimeDetails() {
        System.out.println("=========================================");
        System.out.println("============== RESULTS ==================");
        System.out.println("=========================================");
        System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        for (Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()) {
            //作者自己注释的
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
            System.out.println(getStringForLoopId(loopId) + " ---> " + TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
        }
        System.out.println("=========================================");
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");

        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }

        System.out.println("=========================================");
    }

    protected void manageResources() {
        //发出资源更新 事件
        send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
    }

    private void processTupleFinished(SimEvent ev) {
    }

    @Override
    public void shutdownEntity() {
    }

    public void submitApplication(Application application, int delay, ModulePlacement modulePlacement) {
        //这里是加入 地理位置
        FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
        //把之前的 生成的 系统逻辑架构图 放进来
        getApplications().put(application.getAppId(), application);
        //加入延迟
        getAppLaunchDelays().put(application.getAppId(), delay);
        //加入放置策略
        getAppModulePlacementPolicy().put(application.getAppId(), modulePlacement);

        // 给 监听器 和 执行器 设置逻辑架构图
        for (Sensor sensor : sensors) {
            sensor.setApp(getApplications().get(sensor.getAppId()));
        }
        for (Actuator ac : actuators) {
            ac.setApp(getApplications().get(ac.getAppId()));
        }
        //循环拿到每条边
        for (AppEdge edge : application.getEdges()) {
            if (edge.getEdgeType() == AppEdge.ACTUATOR) {
                String moduleName = edge.getSource();
                // 循环遍历 执行器 判断执行器里 actuatorType属性 是否与 边的目的地相等
                for (Actuator actuator : getActuators()) {
                    if (actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
                        //拿到名字为 edge.getSource() 的虚拟机 设置 执行器 与 任务 的多对一关系
                        // 这里的意图应该是任务到这个虚拟机执行完后  通知多个 执行器 来获取结果
                        application.getModuleByName(moduleName).subscribeActuator(actuator.getId(), edge.getTupleType());
                }
            }
        }
    }

    public void submitApplication(Application application, ModulePlacement modulePlacement) {
        submitApplication(application, 0, modulePlacement);
    }


    private void processAppSubmit(SimEvent ev) {
        Application app = (Application) ev.getData();
        processAppSubmit(app);
    }

    private void processAppSubmit(Application application) {
        System.out.println(CloudSim.clock() + " Submitted application " + application.getAppId());
        FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
        getApplications().put(application.getAppId(), application);

        ModulePlacement modulePlacement = getAppModulePlacementPolicy().get(application.getAppId());

        for (FogDevice fogDevice : fogDevices) {
            //发出 设备激活事件
            sendNow(fogDevice.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
        }
        //拿到设备上虚拟机放置策略
        Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
        for (Integer deviceId : deviceToModuleMap.keySet()) {
            for (AppModule module : deviceToModuleMap.get(deviceId)) {
                //虚拟机提交事件
                sendNow(deviceId, FogEvents.APP_SUBMIT, application);
                //发出虚拟机启动事件
                sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
            }
        }
    }

    public List<FogDevice> getFogDevices() {
        return fogDevices;
    }

    public void setFogDevices(List<FogDevice> fogDevices) {
        this.fogDevices = fogDevices;
    }

    public Map<String, Integer> getAppLaunchDelays() {
        return appLaunchDelays;
    }

    public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
        this.appLaunchDelays = appLaunchDelays;
    }

    public Map<String, Application> getApplications() {
        return applications;
    }

    public void setApplications(Map<String, Application> applications) {
        this.applications = applications;
    }

    public List<Sensor> getSensors() {
        return sensors;
    }

    public void setSensors(List<Sensor> sensors) {
        for (Sensor sensor : sensors)
            sensor.setControllerId(getId());
        this.sensors = sensors;
    }

    public List<Actuator> getActuators() {
        return actuators;
    }

    public void setActuators(List<Actuator> actuators) {
        this.actuators = actuators;
    }

    public Map<String, ModulePlacement> getAppModulePlacementPolicy() {
        return appModulePlacementPolicy;
    }

    public void setAppModulePlacementPolicy(Map<String, ModulePlacement> appModulePlacementPolicy) {
        this.appModulePlacementPolicy = appModulePlacementPolicy;
    }
}