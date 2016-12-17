import com.deft.http.Dhttp
import com.deft.http.Request

/**
 * Created by Administrator on 2016/12/16.
 */
fun main(args:Array<String>){
//    testSync()
    testAsync()
}

fun testAsync() {
    Dhttp.request(Request.Builder().apply{
        url = "http://www.baidu.com"
        timeout = 1000
    }.build(), {request,array ->
        println("success")
        println(request)
        println(String(array))
    }, {result ->
        println("error $result")
    })
}

fun testSync() {
    val response = Dhttp.requestSync(Request.Builder().apply{
        url = "http://www.baidu.com"
        timeout = 1000
    }.build())
    println(response.request)
    println(String(response.data))
}
