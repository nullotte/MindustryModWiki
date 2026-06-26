package wikigen;

import arc.func.*;
import arc.util.*;
import arc.util.Http.*;

public class HttpUtils {
    public static String token = "";

    public static void httpGetAuthorized(String url, ConsT<HttpResponse, Exception> callback) {
        httpGetAuthorized(url, callback, null);
    }

    public static void httpGetAuthorized(String url, ConsT<HttpResponse, Exception> callback, Cons<Throwable> error) {
        Http.get(url).header("Authorization", "Bearer " + token).error(error).block(callback);
    }
}
