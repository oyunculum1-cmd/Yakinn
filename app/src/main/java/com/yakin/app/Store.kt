
fun saveContactRequest(number: String, status: Int) {
    val reqs = store.getStringSet("requests", mutableSetOf()) ?: mutableSetOf()
    reqs.add("$number:$status")
    store.edit().putStringSet("requests", reqs).apply()
}
