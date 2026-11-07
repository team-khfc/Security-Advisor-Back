package kr.securityadvisor.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 영업일 계산(F-13): 주말·기관 공휴일 제외. */
class BusinessCalendarTest {

    // 2026-11-06(금). 2026-11-10(화)을 합성 공휴일로 둔다.
    private static final BusinessCalendar CALENDAR = new BusinessCalendar(Set.of(LocalDate.of(2026, 11, 10)));

    @Test
    @DisplayName("금요일 기준 5영업일 뒤는 주말 2일·공휴일 1일을 건너뛴 다음 주 금요일이다")
    void addBusinessDaysSkipsWeekendAndHoliday() {
        LocalDate friday = LocalDate.of(2026, 11, 6);

        assertThat(CALENDAR.addBusinessDays(friday, 1)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(CALENDAR.addBusinessDays(friday, 2)).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(CALENDAR.addBusinessDays(friday, 5)).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    @DisplayName("남은 영업일은 오늘을 빼고 기한일을 포함해 센다. 지난 날짜는 음수")
    void businessDaysBetween() {
        LocalDate monday = LocalDate.of(2026, 11, 9);

        assertThat(CALENDAR.businessDaysBetween(monday, LocalDate.of(2026, 11, 11))).isEqualTo(1);
        assertThat(CALENDAR.businessDaysBetween(monday, monday)).isZero();
        assertThat(CALENDAR.businessDaysBetween(LocalDate.of(2026, 11, 12), monday)).isEqualTo(-2);
    }

    @Test
    @DisplayName("영업일 수 0 이하는 거부한다")
    void rejectsNonPositive() {
        assertThatThrownBy(() -> CALENDAR.addBusinessDays(LocalDate.of(2026, 11, 6), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
