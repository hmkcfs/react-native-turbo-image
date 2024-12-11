package com.turboimage

import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.request.ImageRequest
import com.facebook.react.bridge.*
import com.facebook.react.bridge.ReactContextBaseJavaModule
import okhttp3.Headers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import coil.disk.DiskCache
import coil.request.CachePolicy

class TurboImageModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {
  private var imageLoader: ImageLoader? = null
  private val successfulPrefetchUris = mutableListOf<String>()
  private val failedPrefetchUris = mutableListOf<String>()
  private var completedRequestCount = 0
  private var totalRequests = 0
  private var isErrorOccurred = false

  override fun getName(): String = REACT_CLASS

  @ReactMethod
  fun prefetch(sources: ReadableArray, cachePolicy: String, promise: Promise) {

    // Initialize counters and lists
    completedRequestCount = 0
    successfulPrefetchUris.clear()
    isErrorOccurred = false
    totalRequests = sources.size()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val imageRequests = sources.toArrayList().map { source ->
          val uri = (source as HashMap<*, *>)["uri"] as String
          val headers = source["headers"] as? HashMap<*, *>
          val cacheKey = source["cacheKey"] as? String

          if (headers != null) {
            val headersBuilder = Headers.Builder()
            headers.map { (key, value) ->
              headersBuilder.add(key as String, value as String)
            }

            val requestBuilder = ImageRequest.Builder(reactApplicationContext)
              .headers(headersBuilder.build())
              .data(uri)
              .diskCacheKey(cacheKey)
              .memoryCacheKey(cacheKey)
              .listener(
                onSuccess = { _, _ ->
                  handleRequestCompletion(true, uri, promise)
                },
                onError = { _, _ ->
                  handleRequestCompletion(false, uri, promise)
                }
              )
            // Set cache policy for Coil image loader
            requestBuilder.diskCachePolicy(CachePolicy.ENABLED)
            requestBuilder.memoryCachePolicy(CachePolicy.ENABLED)
            requestBuilder.build()
          } else {
            val requestBuilder = ImageRequest.Builder(reactApplicationContext)
              .data(uri)
              .diskCacheKey(cacheKey)
              .memoryCacheKey(cacheKey)
              .listener(
                onSuccess = { _, _ ->
                  handleRequestCompletion(true, uri, promise)
                },
                onError = { _, _ ->
                  handleRequestCompletion(false, uri, promise)
                }
              )
            // Set cache policy for Coil image loader
            requestBuilder.diskCachePolicy(CachePolicy.ENABLED)
            requestBuilder.memoryCachePolicy(CachePolicy.ENABLED)
            requestBuilder.build()
          }
        }
        val okHttpClient = OkHttpClient.Builder()
          .addInterceptor { chain ->
            val request = chain.request()
            val newRequest = request.newBuilder()
              .addHeader(
                "Cache-Control",
                "max-age=31536000, public"
              ) // Override server headers
              .build()
            chain.proceed(newRequest)
          }
          .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
          })
          .build()

        imageLoader = Coil.imageLoader(reactApplicationContext).newBuilder()
          .okHttpClient(okHttpClient)
          .respectCacheHeaders(false) // Ensure cache headers from the server are ignored
          .diskCachePolicy(CachePolicy.ENABLED) // Enable disk caching
          .memoryCachePolicy(CachePolicy.ENABLED) // Enable memory caching
          .diskCache {
            DiskCache.Builder()
              .directory(File(reactApplicationContext.cacheDir, "image_cache"))
              .maxSizePercent(0.02)
              .build()
          }
          .apply {
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
          }
          .build()
        imageRequests.forEach { imageRequest ->
          imageLoader?.enqueue(imageRequest)
        }
      } catch (e: Exception) {
        promise.reject("PREFETCH_ERROR", e)
      }
    }
  }

  // Function to handle request completion
  private fun handleRequestCompletion(success: Boolean, uri: String, promise: Promise) {
    completedRequestCount++
    if (!success) {
      isErrorOccurred = true
      failedPrefetchUris.add(uri)
    } else {
      successfulPrefetchUris.add(uri)
    }

    // Check if all requests have completed
    if (completedRequestCount == totalRequests) {
      // Return only successful URIs
      val resultMap = WritableNativeMap()
      val successfulArray = WritableNativeArray()
      val failedArray = WritableNativeArray()

      successfulPrefetchUris.forEach { uri ->
        successfulArray.pushString(uri)
      }
      failedPrefetchUris.forEach { uri ->
        failedArray.pushString(uri)
      }

      resultMap.putArray("successfulUris", successfulArray)
      resultMap.putArray("failedUris", failedArray)

      promise.resolve(resultMap)
    }
  }

  @ReactMethod
  fun dispose(sources: ReadableArray, promise: Promise) {
    val imageRequests = sources.toArrayList().map { source ->
      val uri = (source as HashMap<*, *>)["uri"] as String
      val headers = source["headers"] as? HashMap<*, *>

      if (headers != null) {
        val headersBuilder = Headers.Builder()
        headers.map { (key, value) ->
          headersBuilder.add(key as String, value as String)
        }
        ImageRequest.Builder(context)
          .headers(headersBuilder.build())
          .data(uri)
          .build()
      } else {
        ImageRequest.Builder(context)
          .data(uri)
          .build()
      }
    }
    imageRequests.forEach { imageRequest ->
      imageLoader?.enqueue(imageRequest)?.dispose()
    }
    promise.resolve("Success")
  }

  @ReactMethod
  fun clearMemoryCache(promise: Promise) {
    Coil.imageLoader(context).memoryCache?.clear()
    promise.resolve("Success")
  }

  @OptIn(ExperimentalCoilApi::class)
  @ReactMethod
  fun clearDiskCache(promise: Promise) {
    Coil.imageLoader(context).diskCache?.clear()
    promise.resolve("Success")
  }


  companion object {
    private const val REACT_CLASS = "TurboImageViewManager"
  }
}
