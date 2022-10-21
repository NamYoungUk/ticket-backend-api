package com.sk.bds.ticket.api.data.model.ibm;

import com.softlayer.api.service.Hardware;
import com.softlayer.api.service.virtual.DedicatedHost;
import com.softlayer.api.service.virtual.Guest;
import lombok.Data;

import java.util.List;
import java.util.StringJoiner;

@Data
public class IbmDevice {
    public enum DeviceType {
        VirtualGuest,
        Hardware,
        DedicatedHost,
        Unknown
    }

    public static final String AttributeDelimiter = "/";
    public static final String AttributeDelimiterWithSpace = " / ";

    DeviceType type;
    String domainName;
    long id;

    public IbmDevice() {
        init();
    }

    public IbmDevice(String text) throws IllegalArgumentException {
        init();
        if (text != null && text.contains(AttributeDelimiter)) {
            String[] values = text.split(AttributeDelimiter);
            if (values.length == 3) { //유형/도메인/식별자
                //VirtualGuest/fullyQualifiedDomainName/id
                //Hardware/fullyQualifiedDomainName/id
                //DedicatedHost/HostName/id
                this.type = DeviceType.valueOf(values[0].trim());
                this.domainName = values[1].trim();
                this.id = Long.valueOf(values[2].trim());
            } else if (values.length == 2) { //도메인/식별자
                //fullyQualifiedDomainName/id
                this.type = DeviceType.Unknown;
                this.domainName = values[0].trim();
                this.id = Long.valueOf(values[1].trim());
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    public IbmDevice(Guest guest) {
        init();
        this.type = DeviceType.VirtualGuest;
        if (guest != null) {
            this.domainName = guest.getFullyQualifiedDomainName();
            this.id = guest.getId();
        }
    }

    public IbmDevice(Hardware hardware) {
        init();
        this.type = DeviceType.Hardware;
        if (hardware != null) {
            this.domainName = hardware.getFullyQualifiedDomainName();
            this.id = hardware.getId();
        }
    }

    public IbmDevice(DedicatedHost host) {
        init();
        this.type = DeviceType.DedicatedHost;
        if (host != null) {
            this.domainName = host.getName();
            this.id = host.getId();
        }
    }

    private void init() {
        this.type = DeviceType.Unknown;
        this.domainName = null;
        this.id = 0;
    }

    public boolean isVirtualGuest() {
        return this.type == DeviceType.VirtualGuest;
    }

    public boolean isHardware() {
        return this.type == DeviceType.Hardware;
    }

    public boolean isDedicatedHost() {
        return this.type == DeviceType.DedicatedHost;
    }

    public boolean isUnknownType() {
        return this.type == DeviceType.Unknown;
    }

    public String text(String attributeDelimiter) {
        if(attributeDelimiter == null) {
            attributeDelimiter = AttributeDelimiter;
        }
        return type.name() + attributeDelimiter + domainName + attributeDelimiter + id;
    }

    public String textWithoutType(String attributeDelimiter) {
        if(attributeDelimiter == null) {
            attributeDelimiter = AttributeDelimiter;
        }
        return domainName + attributeDelimiter + id;
    }

    public static String join(String listDelimiter, String attributeDelimiter, List<IbmDevice> devices) {
        StringJoiner joiner = new StringJoiner(listDelimiter);
        for (IbmDevice device : devices) {
            joiner.add(device.text(attributeDelimiter));
        }
        return joiner.toString();
    }

    public static String joinWithoutType(String listDelimiter, String attributeDelimiter, List<IbmDevice> devices) {
        StringJoiner joiner = new StringJoiner(listDelimiter);
        for (IbmDevice device : devices) {
            joiner.add(device.textWithoutType(attributeDelimiter));
        }
        return joiner.toString();
    }
}
