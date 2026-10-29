package com.memoriesmadevisible.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PopupEventDtos {

    private PopupEventDtos() {}

    public record Upsert(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 120) String title,
            @Size(max = 120) String theme,
            @Size(max = 160) String venue,
            @Size(max = 200) String address,
            @Size(max = 60) String time
    ) {}

    public record PrintsUpdate(Integer prints, BigDecimal printPrice) {}

    /** Public view — no sales figures. */
    public record PublicResponse(Long id, LocalDate date, String title, String theme, String venue, String address, String time) {}

    public record AdminResponse(Long id, LocalDate date, String title, String theme, String venue, String address, String time,
                                int prints, BigDecimal printPrice) {}

    public static PublicResponse toPublic(PopupEvent e) {
        return new PublicResponse(e.getId(), e.getDate(), e.getTitle(), e.getTheme(), e.getVenue(), e.getAddress(), e.getTime());
    }

    static AdminResponse toAdmin(PopupEvent e) {
        return new AdminResponse(e.getId(), e.getDate(), e.getTitle(), e.getTheme(), e.getVenue(), e.getAddress(), e.getTime(),
                e.getPrints(), e.getPrintPrice());
    }
}
