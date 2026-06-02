package com.sleepplanner

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
class ApiFlowTest(@Autowired val mvc: MockMvc) {

    @Test
    fun `register, save, list, delete flow works`() {
        val session = MockHttpSession()

        // register
        mvc.perform(
            post("/api/register").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"mama","password":"1234"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("mama"))

        // me
        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("mama"))

        // save
        val body = """
            {"date":"2026-06-02","wh":12,"fw":6,"mw":"07:00","napCount":1,
             "naps":[{"s":"13:00","e":"15:00"}],"napTotal":120,
             "recFirst":"13:00","recNight":"21:00","an":"21:30","actWakeM":750,
             "firstWindowM":360,"eveWindowM":390,"nightDurM":570}
        """.trimIndent()
        mvc.perform(
            post("/api/history").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.updated").value(false))

        // save again -> updated true
        mvc.perform(
            post("/api/history").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.updated").value(true))

        // list
        mvc.perform(get("/api/history").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].date").value("2026-06-02"))
            .andExpect(jsonPath("$[0].actual_night").value("21:30"))
            .andExpect(jsonPath("$[0].actual_wake_m").value(750))
            .andExpect(jsonPath("$[0].nap_count").value(1))

        // delete
        mvc.perform(delete("/api/history/2026-06-02").session(session))
            .andExpect(status().isOk)
        mvc.perform(get("/api/history").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `history requires auth`() {
        mvc.perform(get("/api/history"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login with wrong password is rejected`() {
        val s = MockHttpSession()
        mvc.perform(
            post("/api/register").session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"papa","password":"1234"}""")
        ).andExpect(status().isOk)

        mvc.perform(
            post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"papa","password":"wrong"}""")
        ).andExpect(status().isUnauthorized)

        mvc.perform(
            post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"ghost","password":"1234"}""")
        ).andExpect(status().isNotFound)
    }
}
