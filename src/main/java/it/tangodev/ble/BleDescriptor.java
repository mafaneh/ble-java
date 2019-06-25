package it.tangodev.ble;

import org.bluez.GattDescriptor1;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static it.tangodev.ble.GattPropertyKeys.*;

/**
 * BleDescriptor represents a GattDescriptor that describes a peripheral's characteristic
 * See https://git.kernel.org/pub/scm/bluetooth/bluez.git/tree/doc/gatt-api.txt
 */
public class BleDescriptor implements GattDescriptor1, Properties {
    private static final Logger LOG = LoggerFactory.getLogger(BleDescriptor.class);

    private final String path;
    private final BleCharacteristic characteristic;
    private final String uuid;
    protected byte[] value;
    private DescriptorFlag[] flags;

    public enum DescriptorFlag {
        READ("read"),
        WRITE("write"),
        ENCRYPT_READ("encrypt-read"),
        ENCRYPT_WRITE("encrypt-write"),
        ENCRYPT_AUTHENTICATED_READ("encrypt-authenticated-read"),
        ENCRYPT_AUTHENTICATED_WRITE("encrypt-authenticated-write"),
        SECURE_READ("secure-read"), //Server Only)
        SECURE_WRITE("secure-write"); // (Server Only)

        String flag;

        DescriptorFlag(String flag) {
            this.flag = flag;
        }

        @Override
        public String toString() {
            return this.flag;
        }
    }

    public BleDescriptor(String path, BleCharacteristic characteristic, DescriptorFlag[] flags, String uuid) {
        this.path = path;
        this.characteristic = characteristic;
        this.flags = flags;
        this.uuid = uuid;
    }

    /**
     * This method is called when the central request the Characteristic's value.
     */
    @Override
    public byte[] ReadValue(Map<String, Variant> option) {
        LOG.debug("ReadValue option[" + option + "]");
        int offset = 0;
        if (option.containsKey("offset")) {
            Variant<UInt16> voffset = option.get("offset");
            offset = (voffset.getValue() != null) ? voffset.getValue().intValue() : offset;
        }

        String devicePath = null;
        devicePath = stringVariantToString(option, devicePath);

        byte[] valueBytes = onReadValue(devicePath);
        byte[] slice = Arrays.copyOfRange(valueBytes, offset, valueBytes.length);
        return slice;
    }

    /**
     * This method is called when the central want to write the Characteristic's value.
     */
    @Override
    public void WriteValue(byte[] value, Map<String, Variant> option) {
        LOG.debug("WriteValue Write option[" + option + "]");
        int offset = 0;
        if (option.containsKey("offset")) {
            Variant<UInt16> voffset = option.get("offset");
            offset = (voffset.getValue() != null) ? voffset.getValue().intValue() : offset;
        }

        String devicePath = null;
        onWriteValue(stringVariantToString(option, devicePath), offset, value);
    }

    protected String stringVariantToString(Map<String, Variant> option, String devicePath) {
        if (option.containsKey("device")) {
            Variant<Path> pathVariant = null;
            pathVariant = option.get("pathVariant");
            if (pathVariant != null) devicePath = pathVariant.getValue().getPath();
        }
        return devicePath;
    }

    protected byte[] onReadValue(String devicePath) {
        return value;
    }

    protected void onWriteValue(String devicePath, int offset, byte[] value) {
        this.value = value;
    }

    @Override
    public <A> A Get(String s, String s1) {
        return null;
    }

    @Override
    public <A> void Set(String s, String s1, A a) {

    }

    @Override
    public Map<String, Variant> GetAll(String interfaceName) {
        LOG.debug("GetAll " + interfaceName);
        Map<String, Map<String, Variant>> properties = getProperties();
        if (properties.containsKey(interfaceName)) return properties.get(interfaceName);
        else {
            LOG.error("GetAll Unknown interface: " + interfaceName);
            return null;
        }
    }

    Map<String, Map<String, Variant>> getProperties() {
        LOG.debug("getProperties");

        Map<String, Variant> descriptorMap = new HashMap<>();

        Variant<Path> characteristicPathProperty = new Variant<>(characteristic.getPath());
        descriptorMap.put(CHARACTERISTIC_PROPERTY_KEY, characteristicPathProperty);

        Variant<String> uuidProperty = new Variant<>(uuid);
        descriptorMap.put(UUID_PROPERTY_KEY, uuidProperty);

        Variant<String[]> flagsProperty = new Variant<>(Arrays.stream(flags).map(DescriptorFlag::toString).toArray(String[]::new));
        descriptorMap.put(FLAGS_PROPERTY_KEY, flagsProperty);

//        Variant<byte[]> valueProperty = new Variant<>(value);
//        descriptorMap.put(VALUE_PROPERTY_KEY, valueProperty);

        Map<String, Map<String, Variant>> externalMap = new HashMap<String, Map<String, Variant>>();
        externalMap.put(GattDescriptor1.class.getName(), descriptorMap);

        return externalMap;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    public void export(DBusConnection dBusConnection) throws DBusException {
        LOG.debug("export " + this.path);
        dBusConnection.exportObject(this.path, this);
    }

    public void unexport(DBusConnection dBusConnection) throws DBusException {
        LOG.debug("unexport " + this.path);
        dBusConnection.unExportObject(this.path);
    }

    public String getPath() {
        return path;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public byte[] getValue() {
        return value;
    }

    public DescriptorFlag[] getFlags() {
        return flags;
    }

    public String getUuid() {
        return uuid;
    }
}
