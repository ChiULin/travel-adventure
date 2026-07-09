package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void journeyRequiresValidJwt() throws Exception {
		mockMvc.perform(get("/api/journey/me"))
				.andExpect(status().isForbidden());

		String registration = "{\"username\":\"security-test\",\"password\":\"correct-password\"}";
		String body = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registration))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		Matcher tokenMatch = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(body);
		if (!tokenMatch.find()) {
			throw new AssertionError("Login response did not include a token");
		}

		mockMvc.perform(get("/api/journey/me")
						.header("Authorization", "Bearer " + tokenMatch.group(1)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"security-test\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registration))
				.andExpect(status().isOk());
	}

	@Test
	void registrationValidatesInputAndRejectsDuplicateUsername() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"x\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").isString());

		String registration = "{\"username\":\"duplicate-test\",\"password\":\"correct-password\"}";
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registration))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registration))
				.andExpect(status().isConflict());
	}

}
