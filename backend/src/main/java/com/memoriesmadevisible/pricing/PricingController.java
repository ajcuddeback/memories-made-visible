package com.memoriesmadevisible.pricing;

import com.memoriesmadevisible.publish.PublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public: Pricing page + session types for the booking form. */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PublishService publish;

    public PricingController(PublishService publish) {
        this.publish = publish;
    }

    /** The published price list — admin edits stay in the draft until Publish. */
    @GetMapping
    public List<PricingDtos.SessionTypeView> list() {
        return publish.publishedPricing();
    }
}
