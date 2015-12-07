#import <Foundation/Foundation.h>

typedef void (^ErrorCallback)(NSError*);
typedef void (^DownloaderCallback)(NSNumber*, NSNumber*, NSNumber*);

@interface Downloader : NSObject <NSURLConnectionDelegate>

- (void)downloadFile:(NSString*)urlStr toFile:(NSString*)downloadPath withHeaders:(NSString*)headers callback:(DownloaderCallback)callback errorCallback:(ErrorCallback)errorCallback progressCallback:(DownloaderCallback)progressCallback;

@end
