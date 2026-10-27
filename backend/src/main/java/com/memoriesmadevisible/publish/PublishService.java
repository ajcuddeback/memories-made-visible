package com.memoriesmadevisible.publish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoriesmadevisible.event.PopupEvent;
import com.memoriesmadevisible.event.PopupEventDtos;
import com.memoriesmadevisible.event.PopupEventRepository;
import com.memoriesmadevisible.photo.PhotoStorage;
import com.memoriesmadevisible.photo.SitePhoto;
import com.memoriesmadevisible.photo.SitePhotoRepository;
import com.memoriesmadevisible.pricing.PricingDtos;
import com.memoriesmadevisible.pricing.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Draft vs. live. The admin edits the ordinary tables (the draft); the public
 * site serves from JSON snapshots taken when the admin presses Publish.
 * "Discard" rewrites the draft tables from the snapshots. Bookings and
 * galleries are operational data, not site content, so they are not staged.
 */
@Service
public class PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);
    static final String PRICING = "pricing";
    static final String EVENTS = "events";
    static final String PHOTOS = "photos";

    public record PhotoRef(String key, String contentType) {}
    public record Changes(boolean pricing, boolean events, boolean photos) {
        public boolean any() { return pricing || events || photos; }
    }
    public record Status(boolean dirty, Changes changes, Instant publishedAt) {}

    private static final TypeReference<List<PricingDtos.SessionTypeView>> PRICING_T = new TypeReference<>() {};
    private static final TypeReference<List<PopupEventDtos.PublicResponse>> EVENTS_T = new TypeReference<>() {};
    private static final TypeReference<TreeMap<String, PhotoRef>> PHOTOS_T = new TypeReference<>() {};

    private final PublishedSiteRepository snapshots;
    private final ObjectMapper json;
    private final PricingService pricing;
    private final PopupEventRepository events;
    private final SitePhotoRepository photos;
    private final PhotoStorage storage;

    public PublishService(PublishedSiteRepository snapshots, ObjectMapper json, PricingService pricing,
                          PopupEventRepository events, SitePhotoRepository photos, PhotoStorage storage) {
        this.snapshots = snapshots;
        this.json = json;
        this.pricing = pricing;
        this.events = events;
        this.photos = photos;
        this.storage = storage;
    }

    /** A site that has never been published goes live as it stands, so upgrading never blanks the pages. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensurePublished() {
        if (snapshots.count() == 0) {
            publish();
            log.info("Published the initial site snapshot.");
        }
    }

    // ── what the public site sees ──

    /**
     * The published price list, with package lengths taken from the *current* list: a session's
     * length is scheduling config (it decides the slots offered and what a booking blocks), so it
     * takes effect as soon as it's saved rather than waiting for Publish.
     */
    @Transactional(readOnly = true)
    public List<PricingDtos.SessionTypeView> publishedPricing() {
        var live = pricing.list();
        return read(PRICING, PRICING_T).map(snap -> withLiveDurations(snap, live)).orElse(live);
    }

    private static List<PricingDtos.SessionTypeView> withLiveDurations(List<PricingDtos.SessionTypeView> snap,
                                                                        List<PricingDtos.SessionTypeView> live) {
        Map<String, PricingDtos.SessionTypeView> byKey = new HashMap<>();
        live.forEach(t -> byKey.put(t.key(), t));
        return snap.stream().map(t -> {
            var cur = byKey.get(t.key());
            int typeMinutes = cur == null ? t.durationMinutes() : cur.durationMinutes();
            var tiers = t.tiers().stream().map(tier -> {
                Integer m = null;
                if (cur != null) {
                    m = cur.tiers().stream().filter(x -> x.name().equalsIgnoreCase(tier.name())).map(PricingDtos.Tier::durationMinutes).findFirst().orElse(null);
                }
                if (m == null || m <= 0) m = tier.durationMinutes() != null && tier.durationMinutes() > 0 ? tier.durationMinutes() : (typeMinutes > 0 ? typeMinutes : 60);
                return new PricingDtos.Tier(tier.name(), tier.detail(), tier.price(), tier.includes(), m);
            }).toList();
            return new PricingDtos.SessionTypeView(t.key(), t.name(), t.shortName(), t.description(), t.from(), t.showInPortfolio(), typeMinutes, tiers);
        }).toList();
    }

    /** Lengths are live (above), so they're left out of the snapshot and the "unpublished changes" check. */
    private static List<PricingDtos.SessionTypeView> withoutDurations(List<PricingDtos.SessionTypeView> list) {
        return list.stream().map(t -> new PricingDtos.SessionTypeView(t.key(), t.name(), t.shortName(), t.description(), t.from(),
                t.showInPortfolio(), 0,
                t.tiers().stream().map(x -> new PricingDtos.Tier(x.name(), x.detail(), x.price(), x.includes(), 0)).toList())).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PricingDtos.SessionTypeView> publishedType(String key) {
        if (key == null) return Optional.empty();
        String k = key.trim().toLowerCase();
        return publishedPricing().stream().filter(t -> t.key().equals(k)).findFirst();
    }

    @Transactional(readOnly = true)
    public List<PopupEventDtos.PublicResponse> publishedUpcoming() {
        LocalDate today = LocalDate.now();
        return read(EVENTS, EVENTS_T).orElseGet(this::draftEvents).stream()
                .filter(e -> !e.date().isBefore(today)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, String> publishedPhotoUrls() {
        Map<String, String> out = new LinkedHashMap<>();
        read(PHOTOS, PHOTOS_T).orElseGet(this::draftPhotos).forEach((slot, ref) -> out.put(slot, storage.urlFor(ref.key())));
        return out;
    }

    /** S3 keys the live site references; PhotoService must not delete these on a draft change. */
    @Transactional(readOnly = true)
    public Set<String> publishedPhotoKeys() {
        Set<String> keys = new HashSet<>();
        read(PHOTOS, PHOTOS_T).ifPresent(m -> m.values().forEach(r -> keys.add(r.key())));
        return keys;
    }

    // ── admin ──

    @Transactional(readOnly = true)
    public Status status() {
        var changes = new Changes(
                differs(PRICING, withoutDurations(pricing.list())),
                differs(EVENTS, draftEvents()),
                differs(PHOTOS, draftPhotos()));
        Instant at = snapshots.findAll().stream().map(PublishedSite::getPublishedAt).max(Comparator.naturalOrder()).orElse(null);
        return new Status(changes.any(), changes, at);
    }

    /** Draft → live, for all three kinds at once. */
    @Transactional
    public Status publish() {
        Set<String> previouslyLive = publishedPhotoKeys();
        var draftPhotos = draftPhotos();
        write(PRICING, withoutDurations(pricing.list()));
        write(EVENTS, draftEvents());
        write(PHOTOS, draftPhotos);
        // Objects the old snapshot used that nothing references any more.
        Set<String> stillUsed = new HashSet<>();
        draftPhotos.values().forEach(r -> stillUsed.add(r.key()));
        previouslyLive.stream().filter(k -> !stillUsed.contains(k)).forEach(this::safeDelete);
        return status();
    }

    /** Live → draft: throw away every unpublished change. */
    @Transactional
    public Status discard() {
        // Lengths aren't part of the snapshot; keep the current ones when rewriting the draft.
        read(PRICING, PRICING_T).ifPresent(snap -> pricing.restore(withLiveDurations(snap, pricing.list())));
        read(EVENTS, EVENTS_T).ifPresent(this::restoreEvents);
        read(PHOTOS, PHOTOS_T).ifPresent(this::restorePhotos);
        return status();
    }

    private void restoreEvents(List<PopupEventDtos.PublicResponse> live) {
        Map<Long, PopupEvent> draft = new HashMap<>();
        events.findAll().forEach(e -> draft.put(e.getId(), e));
        Set<Long> keep = new HashSet<>();
        for (var v : live) {
            PopupEvent e = draft.get(v.id());
            if (e == null) e = new PopupEvent(); // deleted in the draft → recreated (prints reset to 0)
            e.setDate(v.date());
            e.setTitle(v.title());
            e.setTheme(v.theme());
            e.setVenue(v.venue());
            e.setTime(v.time());
            e = events.save(e);
            keep.add(e.getId());
        }
        draft.values().stream().filter(e -> !keep.contains(e.getId())).forEach(events::delete);
    }

    private void restorePhotos(Map<String, PhotoRef> live) {
        Set<String> liveKeys = new HashSet<>();
        live.values().forEach(r -> liveKeys.add(r.key()));
        for (var p : photos.findAll()) {
            if (!liveKeys.contains(p.getS3Key())) safeDelete(p.getS3Key()); // draft-only upload
            if (!live.containsKey(p.getSlotId())) photos.delete(p);
        }
        List<SitePhoto> rows = new ArrayList<>();
        live.forEach((slot, ref) -> rows.add(new SitePhoto(slot, ref.key(), ref.contentType())));
        photos.saveAll(rows);
    }

    // ── helpers ──

    private List<PopupEventDtos.PublicResponse> draftEvents() {
        return events.findAllByOrderByDateAsc().stream().map(PopupEventDtos::toPublic)
                .sorted(Comparator.comparing(PopupEventDtos.PublicResponse::date).thenComparing(PopupEventDtos.PublicResponse::id))
                .toList();
    }

    private TreeMap<String, PhotoRef> draftPhotos() {
        TreeMap<String, PhotoRef> m = new TreeMap<>();
        photos.findAll().forEach(p -> m.put(p.getSlotId(), new PhotoRef(p.getS3Key(), p.getContentType())));
        return m;
    }

    private <T> Optional<T> read(String kind, TypeReference<T> type) {
        return snapshots.findById(kind).map(s -> {
            try {
                return json.readValue(s.getPayload(), type);
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void write(String kind, Object value) {
        String payload = serialize(value);
        var row = snapshots.findById(kind).orElse(null);
        if (row == null) {
            snapshots.save(new PublishedSite(kind, payload));
        } else if (!row.getPayload().equals(payload)) {
            row.setPayload(payload);
            snapshots.save(row);
        }
    }

    private boolean differs(String kind, Object draft) {
        return snapshots.findById(kind).map(s -> !s.getPayload().equals(serialize(draft))).orElse(true);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void safeDelete(String key) {
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("Could not delete photo object {}", key, e);
        }
    }
}
