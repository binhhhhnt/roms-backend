package com.roms.service;

import com.roms.dto.request.CreateAreaRequest;
import com.roms.dto.request.UpdateAreaRequest;
import com.roms.dto.request.UpdateAreaStatusRequest;
import com.roms.dto.response.AreaResponse;

import java.util.List;

public interface AreaService {

    List<AreaResponse> getAreas(Boolean isActive);

    AreaResponse createArea(CreateAreaRequest request);

    AreaResponse updateArea(Long id, UpdateAreaRequest request);

    AreaResponse updateAreaStatus(Long id, UpdateAreaStatusRequest request);
}
