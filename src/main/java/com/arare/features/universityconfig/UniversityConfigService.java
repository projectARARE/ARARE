package com.arare.features.universityconfig;

public interface UniversityConfigService {
    UniversityConfigResponse save(UniversityConfigRequest request);
    UniversityConfigResponse getActive();
}
