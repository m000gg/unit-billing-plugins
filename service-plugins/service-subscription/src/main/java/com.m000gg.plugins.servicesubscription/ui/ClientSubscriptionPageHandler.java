package com.m000gg.plugins.servicesubscription.ui;

import com.m000gg.plugin.api.ui.PluginPageHandler;
import com.m000gg.plugin.api.ui.PluginPageRequest;
import com.m000gg.plugin.api.ui.PluginPageResponse;
import com.m000gg.plugins.servicesubscription.application.SubscriptionService;
import com.m000gg.plugins.servicesubscription.domain.PluginId;
import com.m000gg.plugins.servicesubscription.domain.Service;
import com.m000gg.plugins.servicesubscription.domain.Subscription;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionCharge;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionChargeStatus;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.csrfInput;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.escape;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.formatInstant;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.formatPrice;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.parseUuid;

public final class ClientSubscriptionPageHandler implements PluginPageHandler {

    private static final String BASE_PATH = "/client/plugins/" + PluginId.VALUE;

    private final SubscriptionService subscriptionService;

    public ClientSubscriptionPageHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService");
    }

    @Override
    public PluginPageResponse handle(PluginPageRequest request) {
        Objects.requireNonNull(request, "request");

        if ("GET".equalsIgnoreCase(request.method())) {
            return switch (request.path()) {
                case "/" -> renderHome(request);
                case "/charges" -> renderCharges(request);
                default -> PluginPageResponse.notFound();
            };
        }

        if ("POST".equalsIgnoreCase(request.method())) {
            return switch (request.path()) {
                case "/subscribe" -> handleSubscribe(request);
                case "/cancel" -> handleCancel(request);
                default -> PluginPageResponse.notFound();
            };
        }

        return PluginPageResponse.notFound();
    }

    private PluginPageResponse renderHome(PluginPageRequest request) {
        UUID subscriberId = request.userId();
        List<Service> allServices = subscriptionService.listAllServices();
        List<Service> activeServices = subscriptionService.listActiveServices();
        List<Subscription> subscriptions = subscriptionService.findSubscriptionsBySubscriber(subscriberId);

        Map<UUID, Service> servicesById = new HashMap<>();
        for (Service service : allServices) {
            servicesById.put(service.id(), service);
        }

        Map<UUID, Subscription> liveByServiceId = new HashMap<>();
        for (Subscription subscription : subscriptions) {
            if (subscription.status() != SubscriptionStatus.CANCELED) {
                liveByServiceId.put(subscription.serviceId(), subscription);
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"sub-client\">");
        html.append("<div class=\"sub-client__header\">");
        html.append("<p class=\"statement__eyebrow\">{{i18n:client.eyebrow}}</p>");
        html.append("<h1 class=\"statement__title\">{{i18n:client.title}}</h1>");
        html.append("<p class=\"statement__sub sub-client__sub\">{{i18n:client.subtitle}}</p>");
        html.append("</div>");

        appendBanner(html, request.parameter("status").orElse(null));

        // My subscriptions
        html.append("<section class=\"sub-client__section\">");
        html.append("<h2 class=\"sub-client__section-title\">{{i18n:client.mine.heading}}</h2>");

        if (subscriptions.isEmpty()) {
            html.append("<div class=\"sub-client__card\"><p class=\"sub-client__empty\">")
                    .append("{{i18n:client.mine.empty}}</p></div>");
        } else {
            html.append("<div class=\"sub-client__list\">");
            for (Subscription sub : subscriptions) {
                Service svc = servicesById.get(sub.serviceId());
                String serviceName = svc != null ? svc.name() : sub.serviceId().toString();
                String priceText = svc != null ? formatPrice(svc.price()) : "—";

                html.append("<article class=\"sub-client__card\">");
                html.append("<div class=\"sub-client__card-head\">");
                html.append("<div>");
                html.append("<h3 class=\"sub-client__name\">").append(escape(serviceName)).append("</h3>");
                html.append("<div class=\"sub-client__price\">")
                        .append(escape(priceText))
                        .append(" / {{i18n:unit.month}}</div>");
                html.append("</div>");
                html.append("<span class=\"ledger-row__type-badge ")
                        .append(statusBadgeClass(sub.status()))
                        .append("\">")
                        .append(statusI18n(sub.status()))
                        .append("</span>");
                html.append("</div>");

                html.append("<div class=\"sub-client__meta\">");
                if (sub.status() == SubscriptionStatus.ACTIVE) {
                    if (sub.cancelAtPeriodEnd()) {
                        html.append("<span>{{i18n:client.sub.endsAt}}: <strong>")
                                .append(escape(formatInstant(sub.endsAt())))
                                .append("</strong></span>");
                    } else {
                        html.append("<span>{{i18n:client.sub.nextBilling}}: <strong>")
                                .append(escape(formatInstant(sub.nextBillingAt())))
                                .append("</strong></span>");
                    }
                    if (sub.minTermEndsAt() != null && sub.startedAt() != null
                            && sub.minTermEndsAt().isAfter(sub.startedAt())) {
                        html.append("<span>{{i18n:client.sub.minTermEnds}}: ")
                                .append(escape(formatInstant(sub.minTermEndsAt())))
                                .append("</span>");
                    }
                } else if (sub.status() == SubscriptionStatus.SUSPENDED) {
                    html.append("<span class=\"sub-client__warn\">{{i18n:client.sub.suspendedHint}}</span>");
                } else {
                    html.append("<span>{{i18n:client.sub.endedAt}}: ")
                            .append(escape(formatInstant(sub.endsAt())))
                            .append("</span>");
                }
                html.append("</div>");

                if (sub.status() == SubscriptionStatus.SUSPENDED) {
                    html.append("<form class=\"sub-client__subscribe-form\" method=\"post\" action=\"")
                            .append(BASE_PATH).append("/subscribe\">");
                    html.append(csrfInput(request));
                    html.append("<input type=\"hidden\" name=\"serviceId\" value=\"")
                            .append(escape(sub.serviceId().toString())).append("\">");
                    appendLegalConsent(html, svc != null ? svc.minPeriods() : 0);
                    html.append("<div class=\"sub-client__actions\">");
                    html.append("<a class=\"sub-client__btn sub-client__btn--ghost\" href=\"")
                            .append(BASE_PATH).append("/charges?subscriptionId=")
                            .append(escape(sub.id().toString()))
                            .append("\">{{i18n:client.action.charges}}</a>");
                    html.append("<button class=\"sub-client__btn sub-client__btn--primary\" type=\"submit\">");
                    html.append("{{i18n:client.action.retryPay}}");
                    html.append("</button>");
                    html.append("</div>");
                    html.append("</form>");

                    html.append("<div class=\"sub-client__actions\">");
                    html.append("<form method=\"post\" action=\"")
                            .append(BASE_PATH).append("/cancel\">");
                    html.append(csrfInput(request));
                    html.append("<input type=\"hidden\" name=\"subscriptionId\" value=\"")
                            .append(escape(sub.id().toString())).append("\">");
                    html.append("<button class=\"sub-client__btn sub-client__btn--danger\" type=\"submit\">");
                    html.append("{{i18n:client.action.cancel}}");
                    html.append("</button></form>");
                    html.append("</div>");
                } else {
                    html.append("<div class=\"sub-client__actions\">");
                    html.append("<a class=\"sub-client__btn sub-client__btn--ghost\" href=\"")
                            .append(BASE_PATH).append("/charges?subscriptionId=")
                            .append(escape(sub.id().toString()))
                            .append("\">{{i18n:client.action.charges}}</a>");

                    if (sub.status() == SubscriptionStatus.ACTIVE && !sub.cancelAtPeriodEnd()) {
                        html.append("<form method=\"post\" action=\"")
                                .append(BASE_PATH).append("/cancel\">");
                        html.append(csrfInput(request));
                        html.append("<input type=\"hidden\" name=\"subscriptionId\" value=\"")
                                .append(escape(sub.id().toString())).append("\">");
                        html.append("<button class=\"sub-client__btn sub-client__btn--danger\" type=\"submit\">");
                        html.append("{{i18n:client.action.cancel}}");
                        html.append("</button></form>");
                    }
                    html.append("</div>");
                }
                html.append("</article>");
            }
            html.append("</div>");
        }
        html.append("</section>");

        // Available catalog
        html.append("<section class=\"sub-client__section\">");
        html.append("<h2 class=\"sub-client__section-title\">{{i18n:client.catalog.heading}}</h2>");

        if (activeServices.isEmpty()) {
            html.append("<div class=\"sub-client__card\"><p class=\"sub-client__empty\">")
                    .append("{{i18n:client.catalog.empty}}</p></div>");
        } else {
            html.append("<div class=\"sub-client__catalog\">");
            for (Service service : activeServices) {
                Subscription existingLive = liveByServiceId.get(service.id());

                html.append("<article class=\"sub-client__card\">");
                html.append("<h3 class=\"sub-client__name\">").append(escape(service.name())).append("</h3>");
                if (!service.description().isBlank()) {
                    html.append("<p class=\"sub-client__desc\">")
                            .append(escape(service.description())).append("</p>");
                }
                html.append("<div class=\"sub-client__catalog-meta\">");
                html.append("<span class=\"sub-client__price\">")
                        .append(escape(formatPrice(service.price())))
                        .append(" / {{i18n:unit.month}}</span>");
                if (service.minPeriods() > 0) {
                    html.append("<span class=\"sub-client__term\">{{i18n:field.minPeriods}}: ")
                            .append(service.minPeriods())
                            .append(" {{i18n:unit.month}}</span>");
                }
                html.append("</div>");

                if (existingLive != null && existingLive.status() == SubscriptionStatus.ACTIVE) {
                    html.append("<span class=\"ledger-row__type-badge sub-client__badge--active\">")
                            .append("{{i18n:client.catalog.alreadyActive}}</span>");
                } else {
                    html.append("<form class=\"sub-client__subscribe-form\" method=\"post\" action=\"")
                            .append(BASE_PATH).append("/subscribe\">");
                    html.append(csrfInput(request));
                    html.append("<input type=\"hidden\" name=\"serviceId\" value=\"")
                            .append(escape(service.id().toString())).append("\">");
                    appendLegalConsent(html, service.minPeriods());
                    html.append("<button class=\"sub-client__btn sub-client__btn--primary\" type=\"submit\">");
                    html.append(existingLive != null
                            ? "{{i18n:client.action.retryPay}}"
                            : "{{i18n:client.action.subscribe}}");
                    html.append("</button></form>");
                }
                html.append("</article>");
            }
            html.append("</div>");
        }
        html.append("</section>");
        html.append("</div>");

        return PluginPageResponse.html(
                "{{i18n:client.title}}",
                html.toString(),
                List.of(request.assetsBasePath() + "/client.css"),
                List.of()
        );
    }

    private PluginPageResponse renderCharges(PluginPageRequest request) {
        UUID subscriptionId;
        try {
            subscriptionId = parseUuid(request.parameter("subscriptionId").orElse(null));
        } catch (IllegalArgumentException e) {
            return PluginPageResponse.notFound();
        }

        Subscription subscription = subscriptionService.findSubscription(subscriptionId).orElse(null);
        if (subscription == null) {
            return PluginPageResponse.notFound();
        }
        if (!subscription.subscriberId().equals(request.userId())) {
            return PluginPageResponse.forbidden();
        }

        String serviceName = subscriptionService.findService(subscription.serviceId())
                .map(Service::name)
                .orElse(subscription.serviceId().toString());
        List<SubscriptionCharge> charges = subscriptionService.findChargesBySubscription(subscriptionId);

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"sub-client\">");
        html.append("<div class=\"sub-client__header sub-client__header--row\">");
        html.append("<div>");
        html.append("<p class=\"statement__eyebrow\">{{i18n:client.eyebrow}}</p>");
        html.append("<h1 class=\"statement__title\">{{i18n:client.charges.heading}}</h1>");
        html.append("<p class=\"statement__sub sub-client__sub\">").append(escape(serviceName)).append("</p>");
        html.append("</div>");
        html.append("<a class=\"sub-client__btn sub-client__btn--ghost\" href=\"")
                .append(BASE_PATH).append("/\">{{i18n:action.back}}</a>");
        html.append("</div>");

        html.append("<div class=\"ledger\">");
        html.append("<div class=\"ledger__header\">");
        html.append("<h2 class=\"ledger__title\">{{i18n:client.charges.heading}}</h2>");
        html.append("</div>");
        if (charges.isEmpty()) {
            html.append("<div class=\"ledger__empty\">{{i18n:client.charges.empty}}</div>");
        } else {
            html.append("<table class=\"sub-client__table\">");
            html.append("<thead><tr>");
            html.append("<th>{{i18n:client.charges.period}}</th>");
            html.append("<th>{{i18n:field.price}}</th>");
            html.append("<th>{{i18n:field.status}}</th>");
            html.append("<th>{{i18n:client.charges.date}}</th>");
            html.append("</tr></thead><tbody>");

            for (SubscriptionCharge charge : charges) {
                boolean ok = charge.status() == SubscriptionChargeStatus.SUCCESS;
                html.append("<tr>");
                html.append("<td class=\"sub-client__mono\">#").append(charge.periodNo()).append("</td>");
                html.append("<td class=\"sub-client__mono\">").append(escape(formatPrice(charge.amount()))).append("</td>");
                html.append("<td><span class=\"ledger-row__type-badge ")
                        .append(ok ? "sub-client__badge--active" : "sub-client__badge--suspended")
                        .append("\">")
                        .append(ok ? "{{i18n:status.charge.success}}" : "{{i18n:status.charge.failed}}")
                        .append("</span></td>");
                html.append("<td class=\"sub-client__mono\">").append(escape(formatInstant(charge.attemptedAt()))).append("</td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div>");
        html.append("</div>");

        return PluginPageResponse.html(
                "{{i18n:client.charges.heading}}",
                html.toString(),
                List.of(request.assetsBasePath() + "/client.css"),
                List.of()
        );
    }

    private PluginPageResponse handleSubscribe(PluginPageRequest request) {
        if (!"true".equals(request.parameter("termsAccepted").orElse(""))) {
            return PluginPageResponse.redirect("/?status=terms_required");
        }

        try {
            UUID serviceId = parseUuid(request.parameter("serviceId").orElse(null));
            Subscription subscription = subscriptionService.subscribeAndActivate(
                    request.userId(),
                    serviceId,
                    Instant.now()
            );
            if (subscription.status() == SubscriptionStatus.ACTIVE) {
                return PluginPageResponse.redirect("/?status=activated");
            }
            return PluginPageResponse.redirect("/?status=insufficient_funds");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PluginPageResponse.redirect("/?status=error");
        }
    }

    private PluginPageResponse handleCancel(PluginPageRequest request) {
        try {
            UUID subscriptionId = parseUuid(request.parameter("subscriptionId").orElse(null));
            Subscription subscription = subscriptionService.findSubscription(subscriptionId).orElse(null);
            if (subscription == null) {
                return PluginPageResponse.notFound();
            }
            if (!subscription.subscriberId().equals(request.userId())) {
                return PluginPageResponse.forbidden();
            }
            subscriptionService.cancelSubscription(subscriptionId, Instant.now());
            return PluginPageResponse.redirect("/?status=canceled");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PluginPageResponse.redirect("/?status=error");
        }
    }

    private static void appendLegalConsent(StringBuilder html, int minPeriods) {
        html.append("<div class=\"sub-client__legal\">");
        html.append("<p class=\"sub-client__legal-text\">");
        html.append("{{i18n:client.legal.recurring}} ");
        if (minPeriods > 0) {
            html.append("{{i18n:client.legal.minTermPrefix}} <strong>")
                    .append(minPeriods)
                    .append(" {{i18n:unit.month}}</strong>. ");
        } else {
            html.append("{{i18n:client.legal.noMinTerm}} ");
        }
        html.append("{{i18n:client.legal.cancellation}}");
        html.append("</p>");
        html.append("<label class=\"sub-client__checkbox\">");
        html.append("<input type=\"checkbox\" name=\"termsAccepted\" value=\"true\" required>");
        html.append("<span>{{i18n:client.legal.checkbox}}</span>");
        html.append("</label>");
        html.append("</div>");
    }

    private static String statusBadgeClass(SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> "sub-client__badge--active";
            case SUSPENDED -> "sub-client__badge--suspended";
            case CANCELED -> "sub-client__badge--canceled";
        };
    }

    private static String statusI18n(SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> "{{i18n:status.sub.active}}";
            case SUSPENDED -> "{{i18n:status.sub.suspended}}";
            case CANCELED -> "{{i18n:status.sub.canceled}}";
        };
    }

    private static void appendBanner(StringBuilder html, String statusFlag) {
        if (statusFlag == null || statusFlag.isBlank()) {
            return;
        }
        String key;
        boolean error = false;
        switch (statusFlag) {
            case "activated" -> key = "{{i18n:client.msg.activated}}";
            case "canceled" -> key = "{{i18n:client.msg.canceled}}";
            case "insufficient_funds" -> {
                key = "{{i18n:client.msg.insufficientFunds}}";
                error = true;
            }
            case "terms_required" -> {
                key = "{{i18n:client.msg.termsRequired}}";
                error = true;
            }
            case "error" -> {
                key = "{{i18n:msg.invalidInput}}";
                error = true;
            }
            default -> {
                return;
            }
        }
        html.append("<div class=\"sub-client__alert ")
                .append(error ? "sub-client__alert--error" : "sub-client__alert--success")
                .append("\">").append(key).append("</div>");
    }
}
