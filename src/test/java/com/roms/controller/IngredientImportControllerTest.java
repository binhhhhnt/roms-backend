package com.roms.controller;

import com.roms.dto.response.IngredientResponse;
import com.roms.exception.GlobalExceptionHandler;
import com.roms.service.IngredientExcelService;
import com.roms.service.IngredientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngredientImportControllerTest {
    private IngredientService service;
    private IngredientExcelService excelService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IngredientService.class);
        excelService = mock(IngredientExcelService.class);
        mvc = MockMvcBuilders.standaloneSetup(new IngredientController(service, excelService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void importValidRowsReturns201() throws Exception {
        when(service.importIngredients(anyList())).thenReturn(List.of(
                IngredientResponse.builder().id(1L).name("Gạo").unit("kg").build()));
        mvc.perform(post("/api/ingredients/import").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredients":[{"name":"Gạo","unit":"kg","minimumStock":2.5}]}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data[0].name").value("Gạo"));
        verify(service).importIngredients(argThat(rows -> rows.size() == 1
                && rows.getFirst().getMinimumStock().toPlainString().equals("2.5")));
    }

    @Test
    void invalidNestedRowsAndEmptyListsAreRejectedBeforeService() throws Exception {
        for (String body : List.of("{}", "{\"ingredients\":[]}", "{\"ingredients\":[null]}",
                "{\"ingredients\":[{\"name\":\"\",\"unit\":\"kg\"}]}",
                "{\"ingredients\":[{\"name\":\"Gạo\",\"unit\":\"\"}]}",
                "{\"ingredients\":[{\"name\":\"Gạo\",\"unit\":\"kg\",\"minimumStock\":-1}]}")) {
            mvc.perform(post("/api/ingredients/import").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void oversizedBatchIsRejected() throws Exception {
        String rows = String.join(",", Collections.nCopies(501, "{\"name\":\"Gạo\",\"unit\":\"kg\"}"));
        mvc.perform(post("/api/ingredients/import").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[" + rows + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
