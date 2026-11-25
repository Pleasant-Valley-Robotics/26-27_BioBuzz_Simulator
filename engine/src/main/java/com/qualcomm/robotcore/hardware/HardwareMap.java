package com.qualcomm.robotcore.hardware;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sim.SimClock;

/**
 * Stand-in for the SDK HardwareMap. The simulator fills it with the devices of Radical's robot
 * configuration (see sim.hw.RobotHardware). Lookups behave like the real one: a wrong name or a
 * wrong type throws the same IllegalArgumentException the Driver Station would show.
 */
public class HardwareMap implements Iterable<HardwareDevice> {
    public final DeviceMapping<DcMotor> dcMotor = new DeviceMapping<>(DcMotor.class);
    public final DeviceMapping<CRServo> crservo = new DeviceMapping<>(CRServo.class);
    public final DeviceMapping<Servo> servo = new DeviceMapping<>(Servo.class);
    public final DeviceMapping<VoltageSensor> voltageSensor = new DeviceMapping<>(VoltageSensor.class);

    private final Map<String, List<HardwareDevice>> all = new LinkedHashMap<>();

    /** Simulator side: register a device under a configuration name. */
    public void put(String name, HardwareDevice device) {
        all.computeIfAbsent(name, k -> new ArrayList<>()).add(device);
        if (device instanceof DcMotor) dcMotor.put(name, (DcMotor) device);
        if (device instanceof CRServo) crservo.put(name, (CRServo) device);
        if (device instanceof Servo) servo.put(name, (Servo) device);
        if (device instanceof VoltageSensor) voltageSensor.put(name, (VoltageSensor) device);
    }

    public <T> T get(Class<? extends T> classOrInterface, String deviceName) {
        T result = tryGet(classOrInterface, deviceName);
        if (result == null) {
            throw new IllegalArgumentException(String.format(
                    "Unable to find a hardware device with name \"%s\" and type %s",
                    deviceName, classOrInterface.getSimpleName()));
        }
        return result;
    }

    public <T> T tryGet(Class<? extends T> classOrInterface, String deviceName) {
        SimClock.touch();
        List<HardwareDevice> list = all.get(deviceName == null ? null : deviceName.trim());
        if (list != null) {
            for (HardwareDevice d : list) {
                if (classOrInterface.isInstance(d)) return classOrInterface.cast(d);
            }
        }
        return null;
    }

    public HardwareDevice get(String deviceName) {
        List<HardwareDevice> list = all.get(deviceName == null ? null : deviceName.trim());
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Unable to find a hardware device with the name \"%s\"", deviceName));
        }
        return list.get(0);
    }

    public <T> List<T> getAll(Class<? extends T> classOrInterface) {
        List<T> result = new ArrayList<>();
        for (List<HardwareDevice> list : all.values()) {
            for (HardwareDevice d : list) {
                if (classOrInterface.isInstance(d)) result.add(classOrInterface.cast(d));
            }
        }
        return result;
    }

    public Set<String> getNamesOf(HardwareDevice device) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, List<HardwareDevice>> e : all.entrySet()) {
            if (e.getValue().contains(device)) names.add(e.getKey());
        }
        return names;
    }

    public int size() {
        int n = 0;
        for (List<HardwareDevice> list : all.values()) n += list.size();
        return n;
    }

    @Override public Iterator<HardwareDevice> iterator() {
        return getAll(HardwareDevice.class).iterator();
    }

    public class DeviceMapping<DEVICE_TYPE extends HardwareDevice> implements Iterable<DEVICE_TYPE> {
        private final Map<String, DEVICE_TYPE> map = new LinkedHashMap<>();
        private final Class<DEVICE_TYPE> deviceTypeClass;

        public DeviceMapping(Class<DEVICE_TYPE> deviceTypeClass) { this.deviceTypeClass = deviceTypeClass; }

        public Class<DEVICE_TYPE> getDeviceTypeClass() { return deviceTypeClass; }

        public DEVICE_TYPE get(String deviceName) {
            DEVICE_TYPE device = map.get(deviceName == null ? null : deviceName.trim());
            if (device == null) {
                throw new IllegalArgumentException(String.format(
                        "Unable to find a hardware device with the name \"%s\"", deviceName));
            }
            return device;
        }

        public void put(String deviceName, DEVICE_TYPE device) { map.put(deviceName, device); }
        public boolean contains(String deviceName) { return map.containsKey(deviceName); }
        public boolean remove(String deviceName) { return map.remove(deviceName) != null; }
        public Set<Map.Entry<String, DEVICE_TYPE>> entrySet() { return map.entrySet(); }
        public int size() { return map.size(); }
        @Override public Iterator<DEVICE_TYPE> iterator() { return new ArrayList<>(map.values()).iterator(); }
    }
}
