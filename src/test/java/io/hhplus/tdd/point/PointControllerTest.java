package io.hhplus.tdd.point;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PointControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * 케이스: 신규 유저의 포인트 조회 시 0원이어야 한다.
     * 이유: DB 테이블(UserPointTable)은 미존재 유저일 경우 기본값 0으로 반환한다.
     */
    @Test
    @DisplayName("GET /point/{id} - 신규 유저는 0 포인트")
    void getPoint_emptyUser_returnsZero() throws Exception {
        long userId = 101;
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0));

    }

    /**
     * 케이스: 충전 성공 시 잔액이 증가하고 히스토리에 CHARGE가 기록된다.
     * 이유: 요구사항 - 충전은 누적, 기록은 PointHistoryTable에 추가되어야 함.
     */
    @Test
    @DisplayName("PATCH /point/{id}/charge - 잔액 증가 및 CHARGE 히스토리 기록")
    void charge_increaseBalance_and_historyRecorded() throws Exception {
        long userId = 1101;
        long chargeAmount = 1000;

        // when: 충전
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(chargeAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(chargeAmount));

        // then: 잔액 확인
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(chargeAmount));

        // and: 히스토리 확인 (해당 유저의 첫 기록이 CHARGE)
        MvcResult historyResult = mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode histories = objectMapper.readTree(historyResult.getResponse().getContentAsString());
        assertThat(histories.isArray()).isTrue();
        assertThat(histories).hasSizeGreaterThanOrEqualTo(1);
        JsonNode first = histories.get(0);
        assertThat(first.get("userId").asLong()).isEqualTo(userId);
        assertThat(first.get("amount").asLong()).isEqualTo(chargeAmount);
        assertThat(first.get("type").asText()).isEqualTo("CHARGE");
    }

    /**
     * 케이스: 사용 성공 시 잔액이 감소하고 히스토리에 USE가 기록된다.
     * 이유: 요구사항 - 사용은 잔액 차감, 기록은 PointHistoryTable에 추가되어야 함.
     */
    @Test
    @DisplayName("PATCH /point/{id}/use - 잔액 감소 및 USE 히스토리 기록")
    void use_decreaseBalance_and_historyRecorded() throws Exception {
        long userId = 2101;
        long initialCharge = 500;
        long useAmount = 300;

        // given: 잔액 충전
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(initialCharge)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(initialCharge));

        // when: 사용
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(useAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(initialCharge - useAmount));

        // then: 잔액 확인
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(initialCharge - useAmount));

        // and: 히스토리 2건(CHARGE, USE) 확인
        MvcResult historyResult = mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode histories = objectMapper.readTree(historyResult.getResponse().getContentAsString());
        assertThat(histories.isArray()).isTrue();
        assertThat(histories).hasSize(2);
        JsonNode second = histories.get(1);
        assertThat(second.get("userId").asLong()).isEqualTo(userId);
        assertThat(second.get("amount").asLong()).isEqualTo(useAmount);
        assertThat(second.get("type").asText()).isEqualTo("USE");
    }

    /**
     * 케이스: 잔고 부족 시 사용은 500 에러가 발생해야 하며 잔액과 히스토리는 변경되지 않는다.
     * 이유: 요구사항 - 잔고가 부족할 경우 포인트 사용은 실패.
     */
    @Test
    @DisplayName("PATCH /point/{id}/use - 잔고 부족 시 실패(500) 및 상태 불변")
    void use_insufficientBalance_returnsError_and_noStateChange() throws Exception {
        long userId = 3101;
        long tryUse = 100;

        // when: 잔액 없이 사용 시도
        MvcResult errorResult = mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(tryUse)))
                .andExpect(status().isInternalServerError())
                .andReturn();

        // then: 에러 응답 바디 확인(ApiControllerAdvice)
        JsonNode error = objectMapper.readTree(errorResult.getResponse().getContentAsString());
        assertThat(error.get("code").asText()).isEqualTo("500");

        // and: 잔액 불변
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(0));

        // and: 히스토리 없음
        MvcResult historyResult = mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode histories = objectMapper.readTree(historyResult.getResponse().getContentAsString());
        assertThat(histories.isArray()).isTrue();
        assertThat(histories).hasSize(0);
    }
}


