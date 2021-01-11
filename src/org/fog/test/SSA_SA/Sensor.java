package org.fog.test.SSA_SA;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.Tuple;
import org.fog.utils.*;
import org.fog.utils.distribution.Distribution;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Tiger
 * @date 2021/1/8 09:48
 */
public class Sensor extends SimEntity {
    /**
     * 网关设备ID  这个ID表示 Sensor 的上层设备是谁 即 任务传给谁
     */
    private int gatewayDeviceId;
    /**
     * 地理位置
     */
    private GeoLocation geoLocation;
    /**
     * 任务输出大小
     */
    private long outputSize;
    /**
     * 逻辑架构图ID
     */
    private String appId;
    /**
     * 用户ID
     */
    private int userId;
    /**
     * 元组类型（任务类型）
     */
    private String tupleType;
    /**
     * 传感器名称
     */
    private String sensorName;
    /**
     * 目标虚拟机名称
     */
    private String destModuleName;
    /**
     * 传输分布？
     */
    private Distribution transmitDistribution;
    /**
     * controller ID  控制器ID
     */
    private int controllerId;
    /**
     * 逻辑架构图
     */
    private Application app;
    /**
     * 延迟 具体这个延迟干什么用，暂且还不知，看到代码再说
     */
    private double latency;

    public Sensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation,
                  Distribution transmitDistribution, int cpuLength, int nwLength, String tupleType, String destModuleName) {
        super(name);
        this.setAppId(appId);
        this.gatewayDeviceId = gatewayDeviceId;
        this.geoLocation = geoLocation;
        this.outputSize = 3;
        this.setTransmitDistribution(transmitDistribution);
        setUserId(userId);
        setDestModuleName(destModuleName);
        setTupleType(tupleType);
        setSensorName(sensorName);
        setLatency(latency);
    }

    public Sensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation,
                  Distribution transmitDistribution, String tupleType) {
        super(name);
        this.setAppId(appId);
        this.gatewayDeviceId = gatewayDeviceId;
        this.geoLocation = geoLocation;
        this.outputSize = 3;
        this.setTransmitDistribution(transmitDistribution);
        setUserId(userId);
        setTupleType(tupleType);
        setSensorName(sensorName);
        setLatency(latency);
    }

    /**
     * This constructor is called from the code that generates PhysicalTopology from JSON
     *
     * @param name
     * @param tupleType
     * @param string
     * @param userId
     * @param appId
     * @param transmitDistribution
     */
    public Sensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution) {
        super(name);
        this.setAppId(appId);
        this.setTransmitDistribution(transmitDistribution);
        setTupleType(tupleType);
        setSensorName(tupleType);
        setUserId(userId);
    }

    public void transmit() {
        AppEdge _edge = null;
        for (AppEdge edge : getApp().getEdges()) {
            if (edge.getSource().equals(getTupleType()))
                _edge = edge;
        }
        long cpuLength = (long) _edge.getTupleCpuLength();
        long nwLength = (long) _edge.getTupleNwLength();

        Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1, nwLength, outputSize,
                new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        tuple.setUserId(getUserId());
        tuple.setTupleType(getTupleType());

        tuple.setDestModuleName(_edge.getDestination());
        tuple.setSrcModuleName(getSensorName());
        Logger.debug(getName(), "Sending tuple with tupleId = " + tuple.getCloudletId());

        int actualTupleId = updateTimings(getSensorName(), tuple.getDestModuleName());
        tuple.setActualTupleId(actualTupleId);

        send(gatewayDeviceId, getLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
    }

    private int updateTimings(String src, String dest) {
        Application application = getApp();
        for (AppLoop loop : application.getLoops()) {
            if (loop.hasEdge(src, dest)) {

                int tupleId = TimeKeeper.getInstance().getUniqueId();
                if (!TimeKeeper.getInstance().getLoopIdToTupleIds().containsKey(loop.getLoopId()))
                    TimeKeeper.getInstance().getLoopIdToTupleIds().put(loop.getLoopId(), new ArrayList<Integer>());
                TimeKeeper.getInstance().getLoopIdToTupleIds().get(loop.getLoopId()).add(tupleId);
                TimeKeeper.getInstance().getEmitTimes().put(tupleId, CloudSim.clock());
                return tupleId;
            }
        }
        return -1;
    }

    @Override
    public void startEntity() {
        //点进去调的也是 SimEntity.send方法 最终 调用CloudSim.send方法 生成一个SimEvent事件放入 CloudSim里的future
        //    父 id                   0.1 事件之间的间隔               感应器加入事件           地理位置--null 这里作为data传了进去
        send(gatewayDeviceId, CloudSim.getMinTimeBetweenEvents(), FogEvents.SENSOR_JOINED, geoLocation);
        //   感应器id        传输间隔 我们在 new Sensor 时传的             任务加入事件
        // 这里的 send方法 没传 data  猜测这里我们可以修改 帮我们的任务传进去
        send(getId(), getTransmitDistribution().getNextValue(), FogEvents.EMIT_TUPLE);
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.TUPLE_ACK:
                //transmit(transmitDistribution.getNextValue());
                break;
            case FogEvents.EMIT_TUPLE:
                transmit();
                send(getId(), getTransmitDistribution().getNextValue(), FogEvents.EMIT_TUPLE);
                break;
        }

    }

    @Override
    public void shutdownEntity() {

    }

    public int getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public void setGatewayDeviceId(int gatewayDeviceId) {
        this.gatewayDeviceId = gatewayDeviceId;
    }

    public GeoLocation getGeoLocation() {
        return geoLocation;
    }

    public void setGeoLocation(GeoLocation geoLocation) {
        this.geoLocation = geoLocation;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTupleType() {
        return tupleType;
    }

    public void setTupleType(String tupleType) {
        this.tupleType = tupleType;
    }

    public String getSensorName() {
        return sensorName;
    }

    public void setSensorName(String sensorName) {
        this.sensorName = sensorName;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getDestModuleName() {
        return destModuleName;
    }

    public void setDestModuleName(String destModuleName) {
        this.destModuleName = destModuleName;
    }

    public Distribution getTransmitDistribution() {
        return transmitDistribution;
    }

    public void setTransmitDistribution(Distribution transmitDistribution) {
        this.transmitDistribution = transmitDistribution;
    }

    public int getControllerId() {
        return controllerId;
    }

    public void setControllerId(int controllerId) {
        this.controllerId = controllerId;
    }

    public Application getApp() {
        return app;
    }

    public void setApp(Application app) {
        this.app = app;
    }

    public Double getLatency() {
        return latency;
    }

    public void setLatency(Double latency) {
        this.latency = latency;
    }
}
