//
//  HikVideoControl.h
//  AFNetworking
//
//  Created by 郑江荣 on 2019/5/12.
//

#import <UIKit/UIKit.h>
#import <WeexSDK/WeexSDK.h>
#import "PlayView.h"
#import "Mcu_sdk/PlayBackManager.h"
#import "Mcu_sdk/RealPlayManager.h"


@interface HikVideoControl : UIViewController<PlayBackManagerDelegate,RealPlayManagerDelegate>
@property (nonatomic, retain) PlayBackManager   *playBackManager;
@property (nonatomic, retain) RealPlayManager   *realManager;
@property (nonatomic) int    videoType;
@property (nonatomic, retain) PlayView *player;
@property (nonatomic, retain) UIView *parent;
@property (nonatomic, retain) NSString *_id;
@property (nonatomic, retain) NSString *date;
@property (nonatomic) BOOL isFullScreen;
@property (nonatomic) CGFloat kheight;
@property (nonatomic) CGRect origin_frame;
@property (nonatomic, retain) NSString *level;
@property (nonatomic, retain) NSTimer           *refreshTimer;/**< 定时器*/
@property (nonatomic)  WXModuleKeepAliveCallback playbackCallback;
-(void)playBack:(NSMutableDictionary*)param  callback:(WXModuleCallback)callback;
-(void)realPlay:(NSMutableDictionary*)param callback:(WXModuleCallback)callback;
-(void)operate:(NSMutableDictionary*)param;
-(void)updatePlayBack:(NSMutableDictionary*)param  callback:(WXModuleKeepAliveCallback)callback timeCallback:(WXModuleKeepAliveCallback)timeCallback;
-(int)audio:(BOOL)audio;
-(int)enableZoom:(BOOL)zoom;
-(void)stop;
-(void)pause:(BOOL)pause;
- (NSString*)capture:(int)quality;
-(int)startRecord;
-(NSString*)stopRecord;
- (void)enterFullScreen:(BOOL)isFullScreen;
-(void)getPlayTime:(WXModuleCallback)callback;
@end

 
