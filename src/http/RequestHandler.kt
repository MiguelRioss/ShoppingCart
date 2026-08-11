package http

interface RequestHandler {
    fun handle(request: HttpRequest): HttpResponse
}
