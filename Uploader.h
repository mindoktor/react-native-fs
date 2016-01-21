#import <Foundation/Foundation.h>

typedef void (^ErrorCallback)(NSError*);
typedef void (^UploaderCallback)(NSNumber*, NSNumber*, NSNumber*);
typedef void (^UploaderDoneCallback)(NSNumber*, NSData*);

@interface Uploader : NSObject <NSURLConnectionDelegate>

- (void)uploadFile:(NSString *)filepath
            urlStr:(NSString *)urlStr
    attachmentName:(NSString *)attachmentName
  attachmentFileName:(NSString *)attachmentFileName
           headers:(NSString *)headers
          callback:(UploaderDoneCallback)callback
     errorCallback:(ErrorCallback)errorCallback
  progressCallback:(UploaderCallback)progressCallback;

@end
