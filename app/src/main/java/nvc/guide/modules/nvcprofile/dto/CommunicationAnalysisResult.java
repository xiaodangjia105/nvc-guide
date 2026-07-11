package nvc.guide.modules.nvcprofile.dto;

import java.util.List;

public record CommunicationAnalysisResult(
    String observationAnalysis,
    String feelingAnalysis,
    String needAnalysis,
    String requestAnalysis,
    Integer observationScore,
    Integer feelingScore,
    Integer needScore,
    Integer requestScore,
    Integer overallScore,
    String overallAssessment,
    String nvcRewrite,
    List<String> keyImprovements
) {}
