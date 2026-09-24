package com.ittxf.oauth2logindemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class IndexPageRenderingTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRootRedirectsToGithubAuthorization() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).endsWith("/oauth2/authorization/github");
    }

    @Test
    void rendersLoggedInUsernameInBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/").with(oauth2Login()).with(oauth2Client("github")))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn();

        String html = result.getResponse().getContentAsString();

        assertThat(html).containsPattern("(?s)successfully logged in\\s*<span[^>]*>user</span>");
        assertThat(html).containsPattern("(?s)<span[^>]*>User: </span><span>user</span>");
    }

}
