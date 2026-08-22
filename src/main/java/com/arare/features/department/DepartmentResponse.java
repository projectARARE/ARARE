package com.arare.features.department;

import com.arare.features.building.BuildingResponse;
import java.util.List;

public record DepartmentResponse(
    Long id,
    String name,
    String code,
    Long instituteId,
    String instituteName,
    List<BuildingResponse> buildingsAllowed
) {}