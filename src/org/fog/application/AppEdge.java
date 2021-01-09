package org.fog.application;

/**
 * Class represents application edges which connect modules together and represent data dependency between them.
 *
 * @author Harshit Gupta
 */
public class AppEdge {
    /**
     * App Edge源自传感器 表示这条边从传感器开始
     */
    public static final int SENSOR = 1; // App Edge originates from a sensor
    /**
     * App Edge源自执行器 表示这条边从执行器开始
     */
    public static final int ACTUATOR = 2; // App Edge leads to an actuator
    /**
     * App Edge在虚拟机之间
     */
    public static final int MODULE = 3; // App Edge is between application modules

    /**
     * Name of source application module
     * 源名称
     */
    private String source;
    /**
     * Name of destination application module
     * 目标名称
     */
    private String destination;
    /**
     * CPU length (in MIPS) of tuples carried by the application edge
     * 这条边承受的CPU长度（以MIPS为单位）  感觉像是上传速度
     */
    private double tupleCpuLength;
    /**
     * Network length (in bytes) of tuples carried by the application edge
     * 任务长度（字节）？
     */
    private double tupleNwLength;
    /**
     * Type of tuples carried by the application edge
     * 任务类型
     */
    private String tupleType;
    /**
     * Direction of tuples carried by the application edge.
     * 标识任务流向  这个是用 1=Tuple.UP 2=Tuple.DOWN 3=Tuple.ACTUATOR 来表示
     */
    private int direction;
    /**
     * 边类型  由 AppEdge.SENSOR  AppEdge.ACTUATOR  AppEdge.MODULE
     */
    private int edgeType;

    /**
     * Periodicity of application edge (in case it is periodic).
     */
    private double periodicity;
    /**
     * Denotes if the application edge is a periodic edge.
     */
    private boolean isPeriodic;

    public AppEdge() {

    }

    public AppEdge(String source, String destination, double tupleCpuLength,
                   double tupleNwLength, String tupleType, int direction, int edgeType) {
        setSource(source);
        setDestination(destination);
        setTupleCpuLength(tupleCpuLength);
        setTupleNwLength(tupleNwLength);
        setTupleType(tupleType);
        setDirection(direction);
        setEdgeType(edgeType);
        setPeriodic(false);
    }

    public AppEdge(String source, String destination, double periodicity, double tupleCpuLength,
                   double tupleNwLength, String tupleType, int direction, int edgeType) {
        setSource(source);
        setDestination(destination);
        setTupleCpuLength(tupleCpuLength);
        setTupleNwLength(tupleNwLength);
        setTupleType(tupleType);
        setDirection(direction);
        setEdgeType(edgeType);
        setPeriodic(true);
        setPeriodicity(periodicity);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public double getTupleCpuLength() {
        return tupleCpuLength;
    }

    public void setTupleCpuLength(double tupleCpuLength) {
        this.tupleCpuLength = tupleCpuLength;
    }

    public double getTupleNwLength() {
        return tupleNwLength;
    }

    public void setTupleNwLength(double tupleNwLength) {
        this.tupleNwLength = tupleNwLength;
    }

    public String getTupleType() {
        return tupleType;
    }

    public void setTupleType(String tupleType) {
        this.tupleType = tupleType;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(int edgeType) {
        this.edgeType = edgeType;
    }

    public double getPeriodicity() {
        return periodicity;
    }

    public void setPeriodicity(double periodicity) {
        this.periodicity = periodicity;
    }

    public boolean isPeriodic() {
        return isPeriodic;
    }

    public void setPeriodic(boolean isPeriodic) {
        this.isPeriodic = isPeriodic;
    }

    @Override
    public String toString() {
        return "AppEdge [source=" + source + ", destination=" + destination
                + ", tupleType=" + tupleType + "]";
    }


}
