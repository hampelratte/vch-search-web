package de.berlios.vch.search.web;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.WebPageTitleComparator;
import de.berlios.vch.search.ISearchService;
import de.berlios.vch.web.IWebAction;
import de.berlios.vch.web.TemplateLoader;
import de.berlios.vch.web.menu.IWebMenuEntry;
import de.berlios.vch.web.menu.WebMenuEntry;
import de.berlios.vch.web.servlets.VchHttpServlet;

@Component
public class SearchServlet extends VchHttpServlet {

    public static final String PATH = "/search";

    public static final String STATIC_PATH = PATH + "/static";

    @Requires(filter = "(instance.name=vch.web.search)")
    private ResourceBundleProvider rbp;

    @Requires
    private LogService logger;

    @Requires
    private TemplateLoader templateLoader;

    @Requires
    private HttpService httpService;

    @Requires
    private ISearchService searchService;

    private BundleContext ctx;

    private ServiceRegistration<IWebMenuEntry> menuReg;

    public SearchServlet(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String action = req.getParameter("action");
        if (action == null) {
            renderHtml(null, req, resp);
        } else if ("search".equals(action)) {
            String q = req.getParameter("q");

            // execute the search
            IOverviewPage result = search(q, resp);

            // render the response
            if ("XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
                try {
                    renderJson(result, req, resp);
                    return;
                } catch (Exception e) {
                    throw new ServletException(e);
                }
            } else {
                renderHtml(result, req, resp);
            }
        } else if ("parse".equals(action)) {
            try {
                IWebPage page = parse(req, resp);
                if (page instanceof IVideoPage) {
                    resp.setContentType("application/json; charset=utf-8");
                    String video = toJSON((IVideoPage) page, true).toString();
                    String actions = actionsToJSON(getWebActions(), page);
                    String response = "{\"video\":" + video + ",\"actions\":" + actions + "}";
                    resp.getWriter().print(response);
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
                    resp.setContentType("text/plain");
                    resp.getWriter().print("Nested search results are not yet implemented");
                }
                return;
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }

    }

    private void renderJson(IOverviewPage result, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().print(toJSON(result).toString());
    }

    private void renderHtml(IOverviewPage result, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("TITLE", rbp.getResourceBundle().getString("I18N_SEARCH"));
        params.put("ACTION", PATH);
        params.put("NOTIFY_MESSAGES", getNotifyMessages(req));
        List<String> css = new ArrayList<String>();
        css.add(SearchServlet.STATIC_PATH + "/search.css");
        params.put("CSS_INCLUDES", css);

        String q = req.getParameter("q");
        params.put("Q", q);

        if (result != null) {
            params.put("RESULTS", result);
            try {
                int resultCount = 0;
                for (IWebPage page : result.getPages()) {
                    if (page instanceof IOverviewPage) {
                        resultCount += ((IOverviewPage) page).getPages().size();
                    }
                }
                params.put("COUNT", resultCount);
            } catch (Exception e) {
                logger.log(LogService.LOG_ERROR, "Couldn't determine search result count", e);
            }
        }

        String page = templateLoader.loadTemplate("search.ftl", params);
        resp.getWriter().print(page);
    }

    private JSONObject toJSON(IWebPage page) throws Exception {
        if (page instanceof IVideoPage) {
            return toJSON((IVideoPage) page, false);
        } else {
            return toJSON((IOverviewPage) page);
        }
    }

    private JSONObject toJSON(IOverviewPage page) throws Exception {
        Map<String, Object> json = new HashMap<String, Object>();
        json.put("title", page.getTitle());
        json.put("parser", page.getParser());
        if (page.getUri() != null) {
            json.put("uri", page.getUri());
        }

        List<JSONObject> subpages = new ArrayList<JSONObject>();
        for (IWebPage subpage : page.getPages()) {
            subpages.add(toJSON(subpage));
        }
        json.put("pages", subpages);

        return new JSONObject(json);
    }

    private JSONObject toJSON(IVideoPage page, boolean addVideo) {
        Map<String, Object> json = new HashMap<String, Object>();

        json.put("title", page.getTitle());
        json.put("parser", page.getParser());

        if (page.getVchUri() != null) {
            json.put("vchuri", page.getVchUri().toString());
        }
        if (addVideo && page.getVideoUri() != null) {
            json.put("video", page.getVideoUri().toString());
        }
        if (page.getUri() != null) {
            json.put("uri", page.getUri().toString());
        }
        if (page.getDescription() != null) {
            json.put("desc", page.getDescription());
        }
        if (page.getThumbnail() != null) {
            json.put("thumb", page.getThumbnail().toString());
        }
        if (page.getPublishDate() != null) {
            json.put("pubDate", page.getPublishDate().getTimeInMillis());
        }
        if (page.getDuration() > 0) {
            json.put("duration", page.getDuration());
        }
        json.put("isLeaf", true);

        return new JSONObject(json);
    }

    private IWebPage parse(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String id = req.getParameter("id");
        String uri = req.getParameter("uri");
        String isVideoPage = req.getParameter("isVideoPage");
        IWebPage page;
        if ("true".equals(isVideoPage)) {
            page = new VideoPage();
        } else {
            page = new OverviewPage();
        }
        page.setParser(id);
        page.setUri(new URI(uri));
        return searchService.parse(page);
    }

    private IOverviewPage search(String q, HttpServletResponse resp) throws IOException {
        // use the search service to search with different providers
        IOverviewPage results = searchService.search(q);
        try {
            Collections.sort(results.getPages(), new WebPageTitleComparator());
        } catch (Exception e1) {
            logger.log(LogService.LOG_WARNING, "Couldn't sort providers by name", e1);
        }

        return results;
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        post(req, resp);
    }

    private List<IWebAction> getWebActions() {
        List<IWebAction> actions = new LinkedList<IWebAction>();

        ServiceTracker<IWebAction, IWebAction> actionsTracker = new ServiceTracker<IWebAction, IWebAction>(ctx, IWebAction.class, null);
        actionsTracker.open();
        Object[] services = actionsTracker.getServices();
        actionsTracker.close();

        if (services != null) {
            for (Object object : services) {
                IWebAction action = (IWebAction) object;
                actions.add(action);
            }
        }

        return actions;
    }

    private String actionsToJSON(List<IWebAction> webActions, IWebPage page) throws UnsupportedEncodingException, URISyntaxException {
        if (!webActions.isEmpty()) {
            String json = "[";
            for (Iterator<IWebAction> iterator = webActions.iterator(); iterator.hasNext();) {
                IWebAction action = iterator.next();
                json += toJSON(action, page);
                if (iterator.hasNext()) {
                    json += ", ";
                }
            }
            return json += "]";
        } else {
            return "[]";
        }
    }

    private String toJSON(IWebAction action, IWebPage page) throws UnsupportedEncodingException, URISyntaxException {
        Map<String, Object> object = new HashMap<String, Object>();
        object.put("title", action.getTitle());
        object.put("uri", action.getUri(page));
        return new JSONObject(object).toString();
    }

    @Validate
    public void start() throws ServletException, NamespaceException {
        // register search servlet
        httpService.registerServlet(PATH, this, null, null);

        // register web interface menu
        IWebMenuEntry menu = new WebMenuEntry(rbp.getResourceBundle().getString("I18N_SEARCH"));
        menu.setPreferredPosition(Integer.MIN_VALUE + 200);
        menu.setLinkUri("#");
        SortedSet<IWebMenuEntry> childs = new TreeSet<IWebMenuEntry>();
        IWebMenuEntry entry = new WebMenuEntry();
        entry.setTitle(rbp.getResourceBundle().getString("I18N_SEARCH"));
        entry.setLinkUri(SearchServlet.PATH);
        childs.add(entry);
        menu.setChilds(childs);
        menuReg = ctx.registerService(IWebMenuEntry.class, menu, null);
    }

    @Invalidate
    public void stop() {
        // unregister the servlet
        if (httpService != null) {
            httpService.unregister(SearchServlet.PATH);
        }

        // unregister the web menu
        if (menuReg != null) {
            menuReg.unregister();
        }
    }
}
