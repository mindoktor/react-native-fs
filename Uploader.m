#import "Uploader.h"

@interface Uploader()

@property (copy) UploaderDoneCallback callback;
@property (copy) ErrorCallback errorCallback;
@property (copy) UploaderCallback progressCallback;

@property (retain) NSNumber* statusCode;
@property (retain) NSNumber* contentLength;
@property (retain) NSNumber* bytesWritten;

@property (retain) NSMutableData* responseData;

@end

@implementation Uploader

- (void)uploadFile:(NSString *)filepath
            urlStr:(NSString *)urlStr
    attachmentName:(NSString *)attachmentName
attachmentFileName:(NSString *)attachmentFileName
           headers:(NSString *)headers
          callback:(UploaderDoneCallback)callback
     errorCallback:(ErrorCallback)errorCallback
  progressCallback:(UploaderCallback)progressCallback;
{
  _callback = callback;
  _errorCallback = errorCallback;
  _progressCallback = progressCallback;

  _bytesWritten = 0;
  _responseData = [NSMutableData data];

  NSURL* url = [NSURL URLWithString:urlStr];

  NSMutableURLRequest* uploadRequest = [NSMutableURLRequest requestWithURL:url
                                                               cachePolicy:NSURLRequestUseProtocolCachePolicy
                                                           timeoutInterval:30];

  [uploadRequest setHTTPMethod:@"POST"];
  
  NSArray *splitHeaders = [headers componentsSeparatedByString: @"\n"];
  
  for (id splitHeader in splitHeaders) {
    NSArray *splitValues = [splitHeader componentsSeparatedByString: @":"];
    [uploadRequest addValue:splitValues[1] forHTTPHeaderField:splitValues[0]];
  }

  NSString *boundary = @"---------14737809831466499882746641449";
  NSString *contentType = [NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary];
  [uploadRequest addValue:contentType forHTTPHeaderField: @"Content-Type"];
  
  NSMutableData *postData = [NSMutableData data];
  [postData appendData:[[NSString stringWithFormat:@"\r\n--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
  [postData appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"%@\"; filename=\".%@\"\r\n",attachmentName, attachmentFileName] dataUsingEncoding:NSUTF8StringEncoding]];
  [postData appendData:[@"Content-Type: application/octet-stream\r\n\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
  [postData appendData:[NSData dataWithContentsOfFile:filepath]];
  [postData appendData:[[NSString stringWithFormat:@"\r\n--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
  
  [uploadRequest setHTTPBody:postData];
  
  NSURLConnection* connection = [[NSURLConnection alloc] initWithRequest:uploadRequest delegate:self startImmediately:NO];
  [connection scheduleInRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
  [connection start];
}

- (void)connection:(NSURLConnection*)connection didFailWithError:(NSError*)error
{
  return _errorCallback(error);
}

- (void)connection:(NSURLConnection*)connection didReceiveResponse:(NSURLResponse*)response
{
  NSHTTPURLResponse* httpUrlResponse = (NSHTTPURLResponse*)response;
  _statusCode = [NSNumber numberWithLong:httpUrlResponse.statusCode];
}

- (void)connection:(NSURLConnection *)connection didSendBodyData:(NSInteger)bytesWritten totalBytesWritten:(NSInteger)totalBytesWritten totalBytesExpectedToWrite:(NSInteger)totalBytesExpectedToWrite
{
  _contentLength = [NSNumber numberWithLong:totalBytesExpectedToWrite];
  _bytesWritten = [NSNumber numberWithLong:totalBytesWritten];
  return _progressCallback([NSNumber numberWithInt:-1], _contentLength, _bytesWritten);
}

- (void)connection:(NSURLConnection*)connection didReceiveData:(NSData*)data
{
  if ([_statusCode isEqualToNumber:[NSNumber numberWithInt:201]]) {
    [_responseData appendData:data];
  }
}

- (void)connectionDidFinishLoading:(NSURLConnection*)connection
{
  return _callback(_statusCode, _responseData);
}

@end
