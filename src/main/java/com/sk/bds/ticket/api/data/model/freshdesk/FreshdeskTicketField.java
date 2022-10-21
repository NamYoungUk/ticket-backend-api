package com.sk.bds.ticket.api.data.model.freshdesk;

public class FreshdeskTicketField {
    public static final String Id = "id";
    public static final String Type = "type";
    public static final String Source = "source";
    public static final String Email = "email";
    public static final String RequesterId = "requester_id";
    public static final String RespondereId = "responder_id";
    public static final String ToEmails = "to_emails";
    public static final String CcEmails = "cc_emails";
    public static final String NotifyEmails = "notify_emails"; //{"description":"Validation failed","errors":[{"code":"invalid_value","field":"notify_emails","message":"Accepts only emails of agents"}]}
    public static final String Attachments = "attachments";
    public static final String AttachmentId = "id";
    public static final String AttachmentUrl = "attachment_url";
    public static final String AttachmentName = "name";
    public static final String AttachmentSize = "size";
    public static final String AttachmentCreatedAt = "created_at";
    public static final String AttachmentUpdatedAt = "updated_at";
    public static final String DescriptionHtml = "description"; //for Ticket
    public static final String DescriptionText = "description_text"; //for Ticket
    public static final String CreatedAt = "created_at";
    public static final String UpdatedAt = "updated_at";
    public static final String Subject = "subject";
    public static final String Priority = "priority";
    public static final String Status = "status";
    public static final String Private = "private";
    public static final String ConversationUserId = "user_id"; //for Conversation
    public static final String ConversationBodyHtml = "body"; //for Conversation
    public static final String ConversationBodyText = "body_text"; //for Conversation
    public static final String Tags = "tags";
    public static final String RelatedTicketIds = "related_ticket_ids"; //List of Ticket IDs which needs to be linked to the Tracker being created.
    public static final String AssociatedTicketList = "associated_tickets_list"; //The 'associated_tickets_list' attribute returns an array of associated ticket IDs based on the association type value

    public static final String CustomFields = "custom_fields";
    public static final String CfPublicUrl = "cf_public_url";
    public static final String CfCsp = "cf_csp";
    public static final String CfCspAccount = "cf_csp_account"; //"cf_csp_account"; //ex) abc@sk.com/616416889412 or abc@sk.com/IBM1916797
    public static final String CfCspCaseId = "cf_csp_case_id";
    public static final String CfEscalation = "cf_escalation";
    public static final String CfCspDevice = "cf_csp_device";

    //For IBM
    public static final String CfIbmL1 = "cf_ibm_l1";
    public static final String CfIbmL2 = "cf_ibm_l2";
    public static final String CfSolveReason = "cf_solve_reason";

    //For SLA
    public static final String CfTribe = "cf_tribe";
    public static final String CfL1ResponseTime = "cf_l1_response_time";
    public static final String CfL2AssignTime = "cf_l2_assign_time";
    public static final String CfL2ResponseTime = "cf_l2_response_time";
    public static final String CfEscalationTime = "cf_escalation_time";
    public static final String CfCspResponseTime = "cf_csp_response_time";
}
