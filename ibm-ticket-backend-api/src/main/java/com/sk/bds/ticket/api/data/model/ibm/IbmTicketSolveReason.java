package com.sk.bds.ticket.api.data.model.ibm;

public class IbmTicketSolveReason {
    public static final String ClientError = "Client error";
    public static final String DefectFoundWithComponentAndService = "Defect found with Component/Service";
    public static final String DocumentationError = "Documentation Error";
    public static final String SolutionFoundInForums = "Solution found in forums";
    public static final String SolutionFoundInPublicDocumentation = "Solution found in public documentation";
    public static final String SolutionNoLongerRequired = "Solution no longer required";
    public static final String SolutionProvidedByIbmOutsideOfSupportCase = "Solution provided by IBM outside of support case";
    public static final String SolutionProvidedByIbmSupportEngineer = "Solution provided by IBM Support engineer";
    public static final String AdministrativeClose = "Administrative Close";
    public static final String TheOthers = "The others";


    public static final String[] SolveReasons = {
            ClientError,
            DefectFoundWithComponentAndService,
            DocumentationError,
            SolutionFoundInForums,
            SolutionFoundInPublicDocumentation,
            SolutionNoLongerRequired,
            SolutionProvidedByIbmOutsideOfSupportCase,
            SolutionProvidedByIbmSupportEngineer,
            AdministrativeClose,
            TheOthers
    };
}
