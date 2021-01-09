package org.fog.test.SSA_SA;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.placement.ModuleMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.*;


/**
 * @author Tiger
 * @date 2021/1/7 22:48
 */
public class Runner {
    /**
     * 保存各个设备 包括 云，边缘服务器，边缘设备
     */
    static List<FogDevice> fogDevices = new ArrayList<>();

    /**
     * 设备的 id
     */
    static Map<Integer, FogDevice> deviceById = new HashMap<>();

    /**
     * 监听器，iFogSim里 发送 任务用的
     */
    static List<Sensor> sensors = new ArrayList<>();

    /**
     * 执行器，iFogSim里 接收 任务结果用的
     */
    static List<Actuator> actuators = new ArrayList<>();

    /**
     * 保存终端设备
     */
    static List<Integer> idOfEndDevices = new ArrayList<>();

//    static Map<Integer, Map<String, Double>> deadlineInfo = new HashMap<>();
//
//    static Map<Integer, Map<String, Integer>> additionalMipsInfo = new HashMap<>();

    /**
     * 感应间隔
     */
    static double sensingInterval = 5;

    static {
        CloudSim.init(1, Calendar.getInstance(), false);

        String appId = "SSA-SA";
        //创建雾代理者
        FogBroker broker = null;
        try {
            broker = new FogBroker("broker");

            // 构建 边缘服务器 以及 移动设备
            createFogDevices(broker.getId(), appId);

            // 构建 边缘服务器与移动设备逻辑架构图  这里初始化了 三种虚拟机的配置 以及三种虚拟机之间的逻辑图（数据流向）
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            //保存虚拟机与设备之间的映射关系  真正的虚拟机与设备分配关系 是在 ModulePlacement 里的 mapModules()方法
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

            //这里把 storageModule（存储型虚拟机） 放到了 之前初始化的 边缘服务器上
            moduleMapping.addModuleToDevice("storageModule", "mecServer");
            //这里把 mainModule（存储型虚拟机） 放到了 之前初始化的 边缘服务器上
            moduleMapping.addModuleToDevice("mainModule", "mecServer");

            // 循环给每个终端设备（移动设备）设置 虚拟机
            for (Integer idOfEndDevice : idOfEndDevices) {
                FogDevice fogDevice = deviceById.get(idOfEndDevice);
                //给每个 移动设备 分配了一个 clientModule（客户端虚拟机）
                moduleMapping.addModuleToDevice("clientModule", fogDevice.getName());
            }
            // 这里把 fogDevices sensors actuators 注册进 controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            controller.submitApplication(application, 0, new ModulePlacement(fogDevices, sensors, actuators, application, moduleMapping, "clientModule"));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {

    }

    private static Application createApplication(String appId, int userId) {

        Application application = Application.createApplication(appId, userId);
        //初始化三种虚拟机配置  内存，每秒可以处理的百万指令数，大小？，带宽
        application.addAppModule("clientModule", 10, 1000, 1000, 100);
        application.addAppModule("mainModule", 50, 1500, 4000, 800);
        application.addAppModule("storageModule", 10, 50, 12000, 100);

        //三种虚拟机之间的依赖关系  这里的关系 是用 边（AppEdge） 来表示的  看图 placement policy.png
        /*从 source -> destination 这里 source 为 IoTSensor 容易跟 tupleType 为 IoTSensor 造成误解
           这里其实叫什么都行 他这里是从逻辑上来说的 因为逻辑上就是从传感器到虚拟机。tupleType表示这条边主要传输任务类型为 IoTSensor 的数据
        */
        application.addAppEdge("IoTSensor", "clientModule", 100, 200, "IoTSensor", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("clientModule", "mainModule", 6000, 600, "RawData", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mainModule", "storageModule", 1000, 300, "StoreData", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("mainModule", "clientModule", 100, 50, "ResultData", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("clientModule", "IoTActuator", 100, 50, "Response", Tuple.DOWN, AppEdge.ACTUATOR);

        //数据（任务）经过虚拟机的流向  看图 placement policy.png
        application.addTupleMapping("clientModule", "IoTSensor", "RawData", new FractionalSelectivity(1.0));
        application.addTupleMapping("mainModule", "RawData", "ResultData", new FractionalSelectivity(1.0));
        application.addTupleMapping("mainModule", "RawData", "StoreData", new FractionalSelectivity(1.0));
        application.addTupleMapping("clientModule", "ResultData", "Response", new FractionalSelectivity(1.0));

        // 给终端设备 设置 时延期限 和 额外的计算要求  这里暂且不知是干什么用的。
//        for (int id : idOfEndDevices) {
//            Map<String, Double> moduleDeadline = new HashMap<>();
//            moduleDeadline.put("mainModule", getValue(3.00, 5.00));
//            Map<String, Integer> moduleAddMips = new HashMap<>();
//            moduleAddMips.put("mainModule", getValue(0, 300));
//            deadlineInfo.put(id, moduleDeadline);
//            additionalMipsInfo.put(id, moduleAddMips);
//        }

        // 这里控制一个任务从开始到结束的完整流向
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("IoTSensor");
            add("clientModule");
            add("mainModule");
            add("clientModule");
            add("IoTActuator");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};

        application.setLoops(loops);
//        application.setDeadlineInfo(deadlineInfo);
//        application.setAdditionalMipsInfo(additionalMipsInfo);
        return application;
    }

    private static double getValue(double min, double max) {
        Random r = new Random();
        return r.nextDouble() % (max - min + 1) + min;
    }

    private static int getValue(int min, int max) {
        Random r = new Random();
        return r.nextInt() % (max - min + 1) + min;
    }

    /**
     * 初始化设备，包括 边缘服务器（mecServer），用户设备（ue）
     *
     * @param userId
     * @param appId
     */
    private static void createFogDevices(int userId, String appId) {
        //初始化 边缘服务器
        FogDevice mecServer = createFogDevice("mecServer", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);

        mecServer.setParentId(Integer.MIN_VALUE);
        fogDevices.add(mecServer);
        deviceById.put(mecServer.getId(), mecServer);

        //初始化 移动设备
        FogDevice ue = createFogDevice("ue", 3200, 1000, 10000, 270, 2, 0, 87.53, 82.44);

        ue.setUplinkLatency(2);
        fogDevices.add(ue);
        deviceById.put(ue.getId(), ue);

        ue.setParentId(mecServer.getId());
        idOfEndDevices.add(ue.getId());

        //初始化 传感器
        Sensor sensor = new Sensor("ue-Sensor", "IoTSensor", userId, appId, new DeterministicDistribution(sensingInterval));
        sensors.add(sensor);
        sensor.setGatewayDeviceId(ue.getId());
        sensor.setLatency(6.0);

        //初始化 执行器
        Actuator actuator = new Actuator("ue-Actuator", userId, appId, "IoTActuator");
        actuators.add(actuator);
        actuator.setGatewayDeviceId(ue.getId());
        actuator.setLatency(1.0);
    }

    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<Pe>();

        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );
        List<Host> hostList = new ArrayList<>();
        hostList.add(host);
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
            fogdevice.setLevel(level);
            fogdevice.setMips((int) mips);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fogdevice;
    }
}
