package com.arare.features.institute;

import java.util.List;

public interface InstituteService {
    InstituteResponse create(InstituteRequest req);

    InstituteResponse update(Long id, InstituteRequest req);

    InstituteResponse findById(Long id);

    List<InstituteResponse> findAll();

    void delete(Long id);
}
