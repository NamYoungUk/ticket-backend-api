package com.sk.bds.ticket.api.data.model.ibm;

public class IbmTicketCategory {
    //https://sldn.softlayer.com/python/create_ticket/
    public static final String SupportGroupName = "Support";
    public static final String AccountingGroupName = "Accounting";
    public static final String HardwareGroupName = "Hardware";
    public static final String SalesGroupName = "Sales";

    public static final String[] SupportGroup = {
            "API Question",
            "DNS Request",
            "DOS/Abuse Issue",
            "HPCaaS from Rescale",
            "Hardware Firewall Question",
            "Hardware Issue",
            "Hardware Load Balancer Question",
            "Licensing Question",
            "Mail Server Issue",
            "OS Reload Question",
            "Portal Information Question",
            "Private Network Question",
            "Public Network Question",
            "Reboots and Console Access",
            "Security Issue",
            "Storage Question",
            "Transcoding Question"
    };

    public static final String[] AccountingGroup = {
            "Accounting Request",
            "CDN Question", //SysAdmin group
            "Vyatta Question" //SysAdmin group
    };
    public static final String[] HardwareGroup = {
            "Colocation Service Request"
    };
    public static final String[] SalesGroup = {
            "Sales Request",
            "Sales Request - Compute & Infrastructure",
            "Sales Request - Firewall Service",
            "Sales Request - General Question",
            "Sales Request - Network & Security Services",
            "Sales Request - Other Services",
            "Sales Request - Upgrades & Add-ons"
    };

    public static String getGroupNameBySubjectName(String subjectName) {
        for (String subject : SupportGroup) {
            if (subject.equals(subjectName)) {
                return SupportGroupName;
            }
        }
        for (String subject : AccountingGroup) {
            if (subject.equals(subjectName)) {
                return AccountingGroupName;
            }
        }
        for (String subject : HardwareGroup) {
            if (subject.equals(subjectName)) {
                return HardwareGroupName;
            }
        }
        for (String subject : SalesGroup) {
            if (subject.equals(subjectName)) {
                return SalesGroupName;
            }
        }
        return null;
    }
}
