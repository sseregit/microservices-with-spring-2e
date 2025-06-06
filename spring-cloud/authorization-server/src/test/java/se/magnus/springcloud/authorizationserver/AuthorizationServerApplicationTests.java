package se.magnus.springcloud.authorizationserver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {})
@AutoConfigureMockMvc
class AuthorizationServerApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void requestTokenUsingClientCredentialsGrantType() throws Exception {

        this.mvc.perform(post("/oauth2/token")
                .param("grant_type", "client_credentials")
                .header("Authorization", "Basic cmVhZGVyOnJlYWRlci1zZWNyZXQ="))
            .andExpect(status().isOk());
    }

    @Test
    void requestOpenidConfiguration() throws Exception {

        this.mvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk());
    }

    @Test
    void requestJwkSet() throws Exception {

        this.mvc.perform(get("/oauth2/jwks"))
            .andExpect(status().isOk());
    }

}
