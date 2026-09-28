package ke.bscodes.possystemfortavuzigadgetsbackend.config;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the web app built into src/main/resources/static.
 *
 * <p>Controllers (/api/**) are matched first; this only runs for everything else:
 *
 * <ul>
 *   <li>a real file (/assets/app.js) → that file
 *   <li>a page folder (/sales/ in Next.js) → /sales/index.html
 *   <li>a screen URL (/stock/phones) → the app's /index.html
 *   <li>unknown /api/... or missing files → 404
 * </ul>
 *
 * <p>Security: permit these paths and the static files; protect /api/**.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // Vite puts hashed file names in /assets, so they can be cached for a year.
    // Everything else (index.html) gets Spring Security's "no-cache" headers.
    registry
        .addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/assets/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String path, Resource location) throws IOException {
                Resource file = location.createRelative(path);
                if (file.exists() && file.isReadable()) {
                  return file;
                }
                Resource pageIndex =
                    location.createRelative(
                        path.isEmpty() || path.endsWith("/")
                            ? path + "index.html"
                            : path + "/index.html");
                if (pageIndex.exists() && pageIndex.isReadable()) {
                  return pageIndex;
                }
                // API calls and missing files (names with a dot) stay 404.
                if (path.startsWith("api/") || path.contains(".")) {
                  return null;
                }
                Resource app = location.createRelative("index.html");
                return app.exists() ? app : null;
              }
            });
  }
}
