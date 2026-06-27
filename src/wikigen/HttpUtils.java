package wikigen;

import arc.func.*;
import arc.util.*;
import arc.util.Http.*;

public class HttpUtils {
    public static String githubToken;

    public static void httpGetAuthorized(String url, ConsT<HttpResponse, Exception> callback) {
        httpGetAuthorized(url, callback, null);
    }

    public static void httpGetAuthorized(String url, ConsT<HttpResponse, Exception> callback, Cons<Throwable> error) {
        HttpRequest request = Http.get(url);
        if (githubToken != null) {
            request.header("Authorization", "Bearer " + githubToken);
        }
        request.error(error);
        request.block(callback);
    }
}
