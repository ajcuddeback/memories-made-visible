package com.memoriesmadevisible.event;

import com.memoriesmadevisible.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/events")
public class AdminPopupEventController {

    private final PopupEventRepository events;

    public AdminPopupEventController(PopupEventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<PopupEventDtos.AdminResponse> list() {
        return events.findAllByOrderByDateAsc().stream().map(PopupEventDtos::toAdmin).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PopupEventDtos.AdminResponse create(@Valid @RequestBody PopupEventDtos.Upsert body) {
        var e = new PopupEvent();
        apply(e, body);
        return PopupEventDtos.toAdmin(events.save(e));
    }

    @PutMapping("/{id}")
    public PopupEventDtos.AdminResponse update(@PathVariable Long id, @Valid @RequestBody PopupEventDtos.Upsert body) {
        var e = find(id);
        apply(e, body);
        return PopupEventDtos.toAdmin(events.save(e));
    }

    @PatchMapping("/{id}/prints")
    public PopupEventDtos.AdminResponse prints(@PathVariable Long id, @RequestBody PopupEventDtos.PrintsUpdate body) {
        var e = find(id);
        if (body.prints() != null) e.setPrints(body.prints());
        if (body.printPrice() != null) e.setPrintPrice(body.printPrice());
        return PopupEventDtos.toAdmin(events.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        events.delete(find(id));
        return ResponseEntity.noContent().build();
    }

    private PopupEvent find(Long id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Pop-up " + id + " not found"));
    }

    private static void apply(PopupEvent e, PopupEventDtos.Upsert b) {
        e.setDate(b.date());
        e.setTitle(b.title().trim());
        e.setTheme(orDefault(b.theme(), "Themed set"));
        e.setVenue(orDefault(b.venue(), "Polk County"));
        e.setAddress(b.address() == null || b.address().isBlank() ? null : b.address().strip());
        e.setTime(orDefault(b.time(), "Hours TBA"));
    }

    private static String orDefault(String s, String dflt) {
        return s == null || s.isBlank() ? dflt : s.trim();
    }
}
