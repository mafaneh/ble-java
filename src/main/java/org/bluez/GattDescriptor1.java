package org.bluez;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Variant;

import java.util.Map;

public interface GattDescriptor1 extends DBusInterface {
    public byte[] ReadValue(Map<String, Variant> option);

    public void WriteValue(byte[] value, Map<String, Variant> option);

}
