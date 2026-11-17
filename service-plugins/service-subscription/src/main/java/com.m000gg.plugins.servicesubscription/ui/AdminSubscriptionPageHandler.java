package com.m000gg.plugins.servicesubscription.ui;

import com.m000gg.plugin.api.ui.PluginPageHandler;
import com.m000gg.plugin.api.ui.PluginPageRequest;
import com.m000gg.plugin.api.ui.PluginPageResponse;
import com.m000gg.plugins.servicesubscription.application.SubscriptionService;
import com.m000gg.plugins.servicesubscription.domain.PluginId;
import com.m000gg.plugins.servicesubscription.domain.Service;
import com.m000gg.plugins.servicesubscription.domain.ServiceStatus;
import com.m000gg.plugins.servicesubscription.domain.Subscription;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.csrfInput;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.escape;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.formatInstant;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.formatPrice;
import static com.m000gg.plugins.servicesubscription.ui.HtmlSupport.parseUuid;

public final class AdminSubscriptionPageHandler implements PluginPageHandler {

    private static final String BASE_PATH = "/admin/plugins/" + PluginId.VALUE;

    private final SubscriptionService subscriptionService;

    public AdminSubscriptionPageHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService");
    }

    @Override
    public PluginPageResponse handle(PluginPageRequest request) {
        Objects.requireNonNull(request, "request");

        if ("GET".equalsIgnoreCase(request.method())) {
            return switch (request.path()) {
                case "/" -> renderCatalog(request);
                case "/services/edit" -> renderEditPage(request);
                default -> PluginPageResponse.notFound();
            };
        }

        if ("POST".equalsIgnoreCase(request.method())) {
            return switch (request.path()) {
                case "/services/create" -> handleCreate(request);
                case "/services/update" -> handleUpdate(request);
                case "/services/delete" -> handleDelete(request);
                case "/subscriptions/cancel" -> handleAdminCancelSubscription(request);
                default -> PluginPageResponse.notFound();
            };
        }

        return PluginPageResponse.notFound();
    }

    private PluginPageResponse renderCatalog(PluginPageRequest request) {
        List<Service> services = subscriptionService.listAllServices();
        List<Subscription> subscriptions = subscriptionService.listAllSubscriptions();

        Map<UUID, Service> servicesById = new HashMap<>();
        for (Service service : services) {
            servicesById.put(service.id(), service);
        }

        Set<UUID> subscriberIds = subscriptions.stream()
                .map(Subscription::subscriberId)
                .collect(Collectors.toSet());
        Map<UUID, String> emailsById = subscriptionService.findSubscriberEmails(subscriberIds);

        String statusFlag = request.parameter("status").orElse(null);

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"sub-admin\">");

        html.append("<div class=\"page-header\">");
        html.append("<p class=\"eyebrow\">{{i18n:admin.eyebrow}}</p>");
        html.append("<h1>{{i18n:admin.title}}</h1>");
        html.append("<p class=\"page-subtitle\">{{i18n:admin.subtitle}}</p>");
        html.append("</div>");

        appendBanner(html, statusFlag);

        html.append("<div class=\"sub-admin__grid\">");

        // Create service card
        html.append("<section class=\"sub-admin__card\">");
        html.append("<h2 class=\"sub-admin__card-title\">{{i18n:admin.create.heading}}</h2>");
        html.append("<form class=\"sub-admin__form\" method=\"post\" action=\"")
                .append(BASE_PATH).append("/services/create\">");
        html.append(csrfInput(request));

        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.name}}</span>");
        html.append("<input type=\"text\" name=\"name\" maxlength=\"200\" required>");
        html.append("</label>");

        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.description}}</span>");
        html.append("<textarea name=\"description\" rows=\"3\"></textarea>");
        html.append("</label>");

        html.append("<div class=\"sub-admin__row\">");
        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.price}}</span>");
        html.append("<input type=\"number\" name=\"price\" step=\"0.0001\" min=\"0.0001\" required>");
        html.append("</label>");

        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.minPeriods}}</span>");
        html.append("<input type=\"number\" name=\"minPeriods\" min=\"0\" max=\"120\" value=\"0\" required>");
        html.append("</label>");
        html.append("</div>");

        html.append("<button class=\"sub-admin__btn sub-admin__btn--primary\" type=\"submit\">");
        html.append("{{i18n:admin.create.submit}}");
        html.append("</button>");
        html.append("</form>");
        html.append("</section>");

        // Catalog table card
        html.append("<section class=\"table-card sub-admin__table-section\">");
        html.append("<div class=\"sub-admin__table-header\">");
        html.append("<h2 class=\"sub-admin__card-title\">{{i18n:admin.catalog.heading}}</h2>");
        html.append("</div>");

        if (services.isEmpty()) {
            html.append("<p class=\"sub-admin__empty\">{{i18n:admin.catalog.empty}}</p>");
        } else {
            html.append("<table class=\"user-table\">");
            html.append("<thead><tr>");
            html.append("<th>{{i18n:field.name}}</th>");
            html.append("<th>{{i18n:field.price}}</th>");
            html.append("<th>{{i18n:field.minPeriods}}</th>");
            html.append("<th>{{i18n:field.status}}</th>");
            html.append("<th>{{i18n:field.actions}}</th>");
            html.append("</tr></thead><tbody>");

            for (Service service : services) {
                boolean active = service.serviceStatus() == ServiceStatus.ACTIVE;
                html.append("<tr").append(active ? "" : " class=\"row-deleted\"").append(">");
                html.append("<td>");
                html.append("<div class=\"sub-admin__svc-name\">").append(escape(service.name())).append("</div>");
                if (!service.description().isBlank()) {
                    html.append("<div class=\"sub-admin__svc-desc\">")
                            .append(escape(service.description()))
                            .append("</div>");
                }
                html.append("</td>");
                html.append("<td class=\"sub-admin__mono\">")
                        .append(escape(formatPrice(service.price())))
                        .append(" / {{i18n:unit.month}}</td>");
                html.append("<td>").append(service.minPeriods()).append("</td>");
                html.append("<td><span class=\"status-badge ")
                        .append(active ? "status-active" : "status-inactive")
                        .append("\">")
                        .append(active ? "{{i18n:status.service.active}}" : "{{i18n:status.service.deleted}}")
                        .append("</span></td>");
                html.append("<td><div class=\"sub-admin__actions\">");
                if (active) {
                    html.append("<a class=\"sub-admin__btn sub-admin__btn--ghost\" href=\"")
                            .append(BASE_PATH).append("/services/edit?id=")
                            .append(escape(service.id().toString()))
                            .append("\">{{i18n:action.edit}}</a>");

                    html.append("<form method=\"post\" action=\"")
                            .append(BASE_PATH).append("/services/delete\">");
                    html.append(csrfInput(request));
                    html.append("<input type=\"hidden\" name=\"id\" value=\"")
                            .append(escape(service.id().toString())).append("\">");
                    html.append("<button class=\"sub-admin__btn sub-admin__btn--danger\" type=\"submit\">");
                    html.append("{{i18n:action.delete}}");
                    html.append("</button></form>");
                } else {
                    html.append("<span class=\"sub-admin__muted\">—</span>");
                }
                html.append("</div></td>");
                html.append("</tr>");
            }

            html.append("</tbody></table>");
        }
        html.append("</section>");
        html.append("</div>");

        // All plugin subscriptions table
        html.append("<section class=\"table-card sub-admin__table-section\">");
        html.append("<div class=\"sub-admin__table-header\">");
        html.append("<h2 class=\"sub-admin__card-title\">{{i18n:admin.subs.heading}}</h2>");
        html.append("</div>");

        if (subscriptions.isEmpty()) {
            html.append("<p class=\"sub-admin__empty\">{{i18n:admin.subs.empty}}</p>");
        } else {
            html.append("<table class=\"user-table\">");
            html.append("<thead><tr>");
            html.append("<th>{{i18n:admin.subs.id}}</th>");
            html.append("<th>{{i18n:admin.subs.subscriber}}</th>");
            html.append("<th>{{i18n:admin.subs.service}}</th>");
            html.append("<th>{{i18n:field.status}}</th>");
            html.append("<th>{{i18n:admin.subs.started}}</th>");
            html.append("<th>{{i18n:admin.subs.nextOrEnds}}</th>");
            html.append("<th>{{i18n:admin.subs.minTermEnds}}</th>");
            html.append("<th>{{i18n:admin.subs.period}}</th>");
            html.append("<th>{{i18n:field.actions}}</th>");
            html.append("</tr></thead><tbody>");

            for (Subscription sub : subscriptions) {
                String shortId = sub.id().toString().substring(0, 8) + "…";
                String email = emailsById.get(sub.subscriberId());
                String subscriberLabel = email != null
                        ? email
                        : sub.subscriberId().toString().substring(0, 8) + "…";
                Service svc = servicesById.get(sub.serviceId());
                String serviceName = svc != null ? svc.name() : sub.serviceId().toString();

                Instant nextOrEnds = (sub.cancelAtPeriodEnd() || sub.status() == SubscriptionStatus.CANCELED)
                        ? sub.endsAt()
                        : sub.nextBillingAt();

                html.append("<tr")
                        .append(sub.status() == SubscriptionStatus.CANCELED ? " class=\"row-deleted\"" : "")
                        .append(">");
                html.append("<td><span class=\"id-cell\" title=\"")
                        .append(escape(sub.id().toString())).append("\">")
                        .append(escape(shortId)).append("</span></td>");
                html.append("<td><a class=\"table-link\" href=\"/admin/users/profile/")
                        .append(escape(sub.subscriberId().toString())).append("\">")
                        .append(escape(subscriberLabel)).append("</a></td>");
                html.append("<td>").append(escape(serviceName)).append("</td>");
                html.append("<td><span class=\"status-badge ")
                        .append(subscriptionBadgeClass(sub.status()))
                        .append("\">")
                        .append(subscriptionStatusI18n(sub.status()))
                        .append("</span>");
                if (sub.status() == SubscriptionStatus.ACTIVE && sub.cancelAtPeriodEnd()) {
                    html.append(" <span class=\"sub-admin__tag\">{{i18n:admin.subs.canceling}}</span>");
                }
                html.append("</td>");
                html.append("<td class=\"sub-admin__mono\">").append(escape(formatInstant(sub.startedAt()))).append("</td>");
                html.append("<td class=\"sub-admin__mono\">").append(escape(formatInstant(nextOrEnds))).append("</td>");
                html.append("<td class=\"sub-admin__mono\">").append(escape(formatInstant(sub.minTermEndsAt()))).append("</td>");
                html.append("<td>#").append(sub.nextPeriodNo()).append("</td>");
                html.append("<td><div class=\"sub-admin__actions\">");
                if ((sub.status() == SubscriptionStatus.ACTIVE && !sub.cancelAtPeriodEnd())
                        || sub.status() == SubscriptionStatus.SUSPENDED) {
                    html.append("<form method=\"post\" action=\"")
                            .append(BASE_PATH).append("/subscriptions/cancel\">");
                    html.append(csrfInput(request));
                    html.append("<input type=\"hidden\" name=\"subscriptionId\" value=\"")
                            .append(escape(sub.id().toString())).append("\">");
                    html.append("<button class=\"sub-admin__btn sub-admin__btn--danger\" type=\"submit\">");
                    html.append("{{i18n:client.action.cancel}}");
                    html.append("</button></form>");
                } else {
                    html.append("<span class=\"sub-admin__muted\">—</span>");
                }
                html.append("</div></td>");
                html.append("</tr>");
            }

            html.append("</tbody></table>");
        }
        html.append("</section>");
        html.append("</div>");

        return PluginPageResponse.html(
                "{{i18n:admin.title}}",
                html.toString(),
                List.of(request.assetsBasePath() + "/admin.css"),
                List.of()
        );
    }

    private PluginPageResponse renderEditPage(PluginPageRequest request) {
        UUID serviceId;
        try {
            serviceId = parseUuid(request.parameter("id").orElse(null));
        } catch (IllegalArgumentException e) {
            return PluginPageResponse.notFound();
        }

        Service service = subscriptionService.findService(serviceId).orElse(null);
        if (service == null || service.serviceStatus() == ServiceStatus.DELETED) {
            return PluginPageResponse.notFound();
        }

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"sub-admin\">");
        html.append("<div class=\"sub-admin__top-row\">");
        html.append("<div class=\"page-header\">");
        html.append("<p class=\"eyebrow\">{{i18n:admin.eyebrow}}</p>");
        html.append("<h1>{{i18n:admin.edit.heading}}</h1>");
        html.append("<p class=\"page-subtitle\">").append(escape(service.name())).append("</p>");
        html.append("</div>");
        html.append("<a class=\"sub-admin__btn sub-admin__btn--ghost\" href=\"")
                .append(BASE_PATH).append("/\">{{i18n:action.back}}</a>");
        html.append("</div>");

        appendBanner(html, request.parameter("status").orElse(null));

        html.append("<section class=\"sub-admin__card sub-admin__card--narrow\">");
        html.append("<form class=\"sub-admin__form\" method=\"post\" action=\"")
                .append(BASE_PATH).append("/services/update\">");
        html.append(csrfInput(request));
        html.append("<input type=\"hidden\" name=\"id\" value=\"")
                .append(escape(service.id().toString())).append("\">");

        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.name}}</span>");
        html.append("<input type=\"text\" name=\"name\" maxlength=\"200\" required value=\"")
                .append(escape(service.name())).append("\">");
        html.append("</label>");

        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.description}}</span>");
        html.append("<textarea name=\"description\" rows=\"3\">")
                .append(escape(service.description())).append("</textarea>");
        html.append("</label>");

        html.append("<div class=\"sub-admin__row\">");
        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.price}}</span>");
        html.append("<input type=\"text\" disabled value=\"")
                .append(escape(formatPrice(service.price()))).append("\">");
        html.append("</label>");

        html.append("<label class=\"sub-admin__field\">");
        html.append("<span>{{i18n:field.minPeriods}}</span>");
        html.append("<input type=\"number\" name=\"minPeriods\" min=\"0\" max=\"120\" required value=\"")
                .append(service.minPeriods()).append("\">");
        html.append("</label>");
        html.append("</div>");

        html.append("<button class=\"sub-admin__btn sub-admin__btn--primary\" type=\"submit\">");
        html.append("{{i18n:admin.edit.submit}}");
        html.append("</button>");
        html.append("</form></section>");
        html.append("</div>");

        return PluginPageResponse.html(
                "{{i18n:admin.edit.heading}}",
                html.toString(),
                List.of(request.assetsBasePath() + "/admin.css"),
                List.of()
        );
    }

    private PluginPageResponse handleCreate(PluginPageRequest request) {
        try {
            String name = request.parameter("name").orElse("");
            String description = request.parameter("description").orElse("");
            BigDecimal price = new BigDecimal(request.parameter("price").orElse("").trim());
            int minPeriods = Integer.parseInt(request.parameter("minPeriods").orElse("0").trim());

            subscriptionService.createService(name, description, price, minPeriods, Instant.now());
            return PluginPageResponse.redirect("/?status=created");
        } catch (IllegalArgumentException e) {
            return PluginPageResponse.redirect("/?status=invalid_input");
        }
    }

    private PluginPageResponse handleUpdate(PluginPageRequest request) {
        UUID serviceId = null;
        try {
            serviceId = parseUuid(request.parameter("id").orElse(null));
            String name = request.parameter("name").orElse("");
            String description = request.parameter("description").orElse("");
            int minPeriods = Integer.parseInt(request.parameter("minPeriods").orElse("0").trim());

            subscriptionService.updateService(serviceId, name, description, minPeriods, Instant.now());
            return PluginPageResponse.redirect("/?status=updated");
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (serviceId != null) {
                return PluginPageResponse.redirect("/services/edit?id=" + serviceId + "&status=invalid_input");
            }
            return PluginPageResponse.redirect("/?status=invalid_input");
        }
    }

    private PluginPageResponse handleDelete(PluginPageRequest request) {
        try {
            UUID serviceId = parseUuid(request.parameter("id").orElse(null));
            subscriptionService.deleteService(serviceId, Instant.now());
            return PluginPageResponse.redirect("/?status=deleted");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PluginPageResponse.redirect("/?status=invalid_input");
        }
    }

    private PluginPageResponse handleAdminCancelSubscription(PluginPageRequest request) {
        try {
            UUID subscriptionId = parseUuid(request.parameter("subscriptionId").orElse(null));
            subscriptionService.cancelSubscription(subscriptionId, Instant.now());
            return PluginPageResponse.redirect("/?status=sub_canceled");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PluginPageResponse.redirect("/?status=invalid_input");
        }
    }

    private static String subscriptionBadgeClass(SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> "status-active";
            case SUSPENDED -> "status-suspended";
            case CANCELED -> "status-inactive";
        };
    }

    private static String subscriptionStatusI18n(SubscriptionStatus status) {
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
            case "created" -> key = "{{i18n:admin.msg.created}}";
            case "updated" -> key = "{{i18n:admin.msg.updated}}";
            case "deleted" -> key = "{{i18n:admin.msg.deleted}}";
            case "sub_canceled" -> key = "{{i18n:admin.msg.subCanceled}}";
            case "invalid_input" -> {
                key = "{{i18n:msg.invalidInput}}";
                error = true;
            }
            default -> {
                return;
            }
        }
        html.append("<div class=\"sub-admin__alert ")
                .append(error ? "sub-admin__alert--error" : "sub-admin__alert--success")
                .append("\">").append(key).append("</div>");
    }
}
