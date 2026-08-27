package com.leetduel.problem.problem;

import com.leetduel.problem.dto.ProblemSummaryDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProblemController.class)
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemService problemService;

    @Test
    void summaries_returnsTheBoundedBatchShape() throws Exception {
        UUID problemId = UUID.randomUUID();
        when(problemService.getSummaries(anyList()))
                .thenReturn(List.of(new ProblemSummaryDto(problemId, "two-sum", "Two Sum", Difficulty.EASY)));

        mockMvc.perform(get("/problems/summaries").param("ids", problemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(problemId.toString()))
                .andExpect(jsonPath("$[0].slug").value("two-sum"))
                .andExpect(jsonPath("$[0].title").value("Two Sum"))
                .andExpect(jsonPath("$[0].difficulty").value("EASY"));
    }

    @Test
    void summaries_rejectsMoreThanTheBoundedBatchSize() throws Exception {
        String[] ids = new String[51];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = UUID.randomUUID().toString();
        }

        mockMvc.perform(get("/problems/summaries").param("ids", ids))
                .andExpect(status().isBadRequest());

        verify(problemService, never()).getSummaries(anyList());
    }
}
