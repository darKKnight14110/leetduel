package com.leetduel.user.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicIdentityController.class)
class PublicIdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @Test
    void identities_returnsOnlyProfilesWithPublicUsernames() throws Exception {
        UUID namedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setUserId(namedId);
        profile.setUsername("alice");
        when(userProfileService.getProfiles(any())).thenReturn(List.of(profile));

        mockMvc.perform(get("/users/public-identities")
                        .param("ids", namedId.toString(), missingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(namedId.toString()))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void identities_rejectsMoreThanTheBoundedBatchSize() throws Exception {
        String[] ids = new String[101];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = UUID.randomUUID().toString();
        }

        mockMvc.perform(get("/users/public-identities").param("ids", ids))
                .andExpect(status().isBadRequest());

        verify(userProfileService, never()).getProfiles(any());
    }
}
