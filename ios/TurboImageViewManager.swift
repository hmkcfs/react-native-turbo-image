import Foundation
import Nuke

@objc(TurboImageViewManager)
class TurboImageViewManager: RCTViewManager {

  private var prefetcher: ImagePrefetcher?
  private var successfulPrefetchUris: [String] = []
  private var failedPrefetchUris: [String] = []

  override func view() -> (TurboImageView) {
    return TurboImageView()
  }

  @objc override static func requiresMainQueueSetup() -> Bool {
    return true
  }
}

extension TurboImageViewManager {

  typealias Source = [String: Any]

  @objc
  func prefetch(_ sources: [Source],
                with cachePolicy: String,
                resolve: @escaping RCTPromiseResolveBlock,
                reject: @escaping RCTPromiseRejectBlock) {
    print("TurboImageModuleiOS -> prefetch")
    // Create DispatchGroup
    let dispatchGroupImageCache = DispatchGroup()
    
    // Run tasks on a global background queue with a high QoS
    let dispatchGroupImageCacheQueue = DispatchQueue(label: "com.redone.sync.image.cache.queue", qos: .background)
    
    if(cachePolicy == "dataCache") {
      ImagePipeline.shared = ImagePipeline(configuration: .withDataCache)
    } else {
      ImagePipeline.shared = ImagePipeline()
    }
    
    for source in sources {
      guard let uri = source["uri"] as? String else {
        continue
      }
      guard let url = URL(string: uri) else {
        continue
      }
      dispatchGroupImageCache.enter()
      print("dispatchGroupImageCacheQueue enter")
      dispatchGroupImageCacheQueue.async {
        
        var urlRequest = URLRequest(url: url)
        if var headers = source["headers"] as? [String: String] {
          urlRequest.allHTTPHeaderFields = headers
        }
        
        let imageRequest = ImageRequest(urlRequest: urlRequest)
        ImagePipeline.shared.loadData(with: imageRequest, completion: { result in
          switch result {
          case .success:
            self.successfulPrefetchUris.append(uri) // Image loaded successfully
          case .failure(let error):
            self.successfulPrefetchUris.append(uri) // Image loaded successfully
            
          }
          dispatchGroupImageCache.leave()
          print("dispatchGroupImageCacheQueue leave()")
        })
      }
      
    }
    // Notify when all tasks have finished
    dispatchGroupImageCache.notify(queue: .main) {
      var result = [
        "successfulUris": self.successfulPrefetchUris,
        "failedUris": self.failedPrefetchUris ]
      resolve(result)
    }
  }
  
  @objc
  func dispose(_ sources: [Source],
               resolve: @escaping RCTPromiseResolveBlock,
               reject: @escaping RCTPromiseRejectBlock) {
    let imageRequests: [ImageRequest] = sources.map {
      guard let uri = $0["uri"] as? String,
            let url = URL(string: uri)
      else { return nil }

      var urlRequest = URLRequest(url: url)
      if let headers = $0["headers"] as? [String: String] {
        urlRequest.allHTTPHeaderFields = headers
      }
      return ImageRequest(urlRequest: urlRequest)
    }.compactMap{ $0 }

    prefetcher?.stopPrefetching(with: imageRequests)
    resolve("Success")
  }

  @objc
  func clearMemoryCache(_ resolve: @escaping RCTPromiseResolveBlock,
                        reject: @escaping RCTPromiseRejectBlock) {
    ImageCache.shared.removeAll()
    resolve("Success")
  }
  
  @objc
  func clearDiskCache(_ resolve: @escaping RCTPromiseResolveBlock,
                      reject: @escaping RCTPromiseRejectBlock) {
    ImagePipeline(configuration: .withDataCache).cache.removeAll()
    DataLoader.sharedUrlCache.removeAllCachedResponses()
    resolve("Success")
  }
}
