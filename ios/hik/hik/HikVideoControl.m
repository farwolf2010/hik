//
//  HikVideoControl.m
//  AFNetworking
//
//  Created by 郑江荣 on 2019/5/12.
//

#import "HikVideoControl.h"
#import "farwolf.h"
#import "Masonry.h"
#import <Mcu_sdk/VideoPlaySDK.h>
#import "VPCaptureInfo.h"
#import "farwolf_weex.h"
#import "VPRecordInfo.h"
#define KScreenWidth [[UIScreen mainScreen] bounds].size.width
#define KScreenHeight [[UIScreen mainScreen] bounds].size.height
static const NSInteger buttonInterval = 5;/**< 按钮间距*/

static dispatch_queue_t video_intercom_queue() {
    static dispatch_queue_t url_request_queue;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        url_request_queue = dispatch_queue_create("voice.intercom.queue", DISPATCH_QUEUE_SERIAL);
    });
    
    return url_request_queue;
}
@interface HikVideoControl ()
//@property (nonatomic, weak) HikVideo *controller;
@end


@implementation HikVideoControl

- (void)viewDidLoad {
    [super viewDidLoad];
    self.player=[self createPlayer];
    self.playState=PLAY_STATE_STOPED;
    [self.view addSubview:self.player];
    __weak typeof (self)weakself=self;
    [self.player mas_makeConstraints:^(MASConstraintMaker *make) {
        make.edges.equalTo(weakself.view);
    }];
    self.kheight=self.view.frame.size.height;
    self.origin_frame=self.parent.frame;
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(stopRealPlay) name:UIApplicationDidEnterBackgroundNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(resetRealPlay) name:UIApplicationDidBecomeActiveNotification object:nil];
    [[UIDevice currentDevice] beginGeneratingDeviceOrientationNotifications];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(orientationChanged:) name:UIDeviceOrientationDidChangeNotification object:[UIDevice currentDevice]];
    [self.view addClick:^{
//        if(self.isFullScreen){
            self.isFullScreen=false;
            [self enterFullScreen:false];
//        }
    }];
  
    // Do any additional setup after loading the view.
}


- (void)orientationChanged:(NSNotification *)notify{
    
    UIDevice *device = notify.object;
    switch (device.orientation) {
            
        case UIDeviceOrientationLandscapeLeft:
        {
//            AXLog(@"屏幕向左平躺");
            if (self.isFullScreen) {
                // 如果已全屏，不做任何处理
            }else{
                self.isFullScreen = YES;
            }
        }
            
            break;
            
        case UIDeviceOrientationPortrait:
        {
//            AXLog(@"屏幕直立");
            if (self.isFullScreen) {
                self.isFullScreen = NO;
                
            }else{ // 如果已小窗，不做任何处理
            }
        }
            break;
            
        default:
            NSLog(@"无法辨认");
            break;
    }
    
}

-(PlayView*)createPlayer{
    
    PlayView *v=[PlayView new];
    _playBackManager = [[PlayBackManager alloc]init];
    _realManager = [[RealPlayManager alloc] initWithDelegate:self];
    _playBackManager.delegate = self;
    return v;
    
}
VPRecordInfo *recordInfo;
-(int)startRecord{
 
    self.player.isRecording=true;
   recordInfo = [[VPRecordInfo alloc]init];
    if (![VideoPlayUtility getRecordInfo:self._id toRecordInfo:recordInfo]) {
        NSLog(@"获取录像信息失败");
        return -2;
    }
//    recordInfo.strRecordPath= [ recordInfo.strRecordPath replace:@"Documents" withString:@"Library/Caches"];
    BOOL finish = false;
    if(self.videoType==0){
        finish = [_realManager startRecord:recordInfo];
    }else{
        finish = [_playBackManager startRecord:recordInfo];
    }
    if (!finish) {
        NSLog(@"VP_StartRecord failed");
        return -1;
    } else {
        NSLog(@"开启录像成功，录像路径：%@",recordInfo.strRecordPath);
        return 0;
    }
}

-(NSString*)stopRecord{
    self.player.isRecording=false;
    BOOL finish = false;
    if(self.videoType==0){
        finish = [_realManager stopRecord];
    }else{
        finish = [_playBackManager stopRecord];
    }
    if (finish) {
        NSLog(@"关闭录像成功");
    } else {
        NSLog(@"关闭录像失败");
    }
    
    //下面是对录像进行处理的操作,如果用户项目中没有这项功能需求,可不用关注.
        NSString *oldPath=[PREFIX_SDCARD add:recordInfo.strRecordPath];
  
    return oldPath;
}

- (NSString*)capture:(int)quality{
    //创建抓图信息对象
    VPCaptureInfo *captureInfo = [[VPCaptureInfo alloc] init];
    
    //生成抓图信息
    if (![VideoPlayUtility getCaptureInfo:@"camera01" toCaptureInfo:captureInfo]) {
        NSLog(@"getCaptureInfo failed");
        return nil;
    }
    
    // 设置抓图质量 1-100 越高质量越高
    captureInfo.nPicQuality = quality;
    BOOL result =false;
    //开始抓图操作
    if(self.videoType==0){
        result = [_realManager capture:captureInfo];
    }else{
        result = [_playBackManager capture:captureInfo];
    }
   
    if (result) {
        NSLog(@"截图成功");
        NSString *oldPath=[PREFIX_SDCARD add:captureInfo.strCapturePath];
        return oldPath;
    } else {
        NSLog(@"截图失败");
        return nil;
    }
    //对抓图文件文件的操作,可参照预览视图RealPlayViewController中,对抓图文件的操作处理
}
-(void)realPlay:(NSMutableDictionary*)param callback:(WXModuleCallback)callback{
    self.videoType=0;
    self._id=param[@"id"];
     self.level=param[@"level"];
    if(self.level==nil){
        self.level=@"high";
    }
    PlayView *player=self.player;
//    VP_STREAM_TYPE g_currentQuality = STREAM_MAG;
    [_realManager stopRealPlay];
    [_realManager startRealPlay:self._id videoType:[self getVideoLevel:self.level] playView:player.playView complete:^(BOOL finish,NSString *message) {
          if(callback==nil)
              return;
        //finish返回YES时,代表当前操作成功.finish返回NO时,message会返回预览过程中的失败信息
        if (finish) {
          
            callback(@{@"err":@(0)});
        }else {
            callback(@{@"err":@(-1)});
        }
    }];
}

-(VP_STREAM_TYPE)getVideoLevel:(NSString*)level{
    if([@"fluent" isEqualToString:level]){
        return STREAM_MAG;
    }else if([@"clear" isEqualToString:level]){
        return STREAM_SUB;
    }else{
        return STREAM_MAIN;
    }
}


-(void)playBack:(NSMutableDictionary*)param  callback:(WXModuleCallback)callback{
    self.videoType=1;
    PlayView *player=self.player;
    VP_STREAM_TYPE g_currentQuality = STREAM_MAG;
    [_playBackManager stopPlayBack];
    NSString *_id=param[@"id"];
    NSString *date=param[@"date"];
     self._id=_id;
     self.date=date;
    NSDateFormatter *g_formatter = [[NSDateFormatter alloc] init];
    [g_formatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
    NSDate *_date = [g_formatter dateFromString:date];
    [_playBackManager startPlayBack:_id playView:player.playView date:_date complete:^(BOOL finish, NSString *message) {
        if (!callback) {
            return;
        }
        if (finish) {
            callback(@{@"err":@(0)});
        }else {
            callback(@{@"err":@(-1)});
        }
    }];
    
}

-(void)updatePlayBack:(NSMutableDictionary*)param  callback:(WXModuleKeepAliveCallback)callback timeCallback:(WXModuleKeepAliveCallback)timeCallback{
        NSString *date=param[@"time"];
    NSDateFormatter *g_formatter = [[NSDateFormatter alloc] init];
    [g_formatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
     NSDate *_date = [g_formatter dateFromString:date];
    NSTimeInterval interval = [_date timeIntervalSince1970];
     TIME_STRUCT       startTime;
      [VideoPlayUtility transformNSTimeInterval:interval  toStruct:&startTime];
    
    if ([_playBackManager stopPlayBack]) {
        [_playBackManager updatePlayBackTime:startTime complete:^(BOOL finish, NSString *message) {
            NSLog(@"%@:%@",@(finish),message);
            callback(@{@"finish":@(finish),@"msg":message},true);
        }];
    }
    if (_refreshTimer!=nil) {
        [_refreshTimer invalidate];
        _refreshTimer=nil;
    }
//    self.playbackCallback=timeCallback;
//    _refreshTimer = [NSTimer scheduledTimerWithTimeInterval:0.5 target:self selector:@selector(updateUITime:) userInfo:nil repeats:YES];
//    [[NSRunLoop currentRunLoop] addTimer:_refreshTimer forMode:NSRunLoopCommonModes];
//    [_refreshTimer setFireDate:[NSDate distantFuture]];
//    [_refreshTimer fire];
}

-(void)queryRecordInfo:(NSMutableDictionary*)param callback:(WXModuleCallback)callback{
    NSString *_id=param[@"id"];
    NSString *date=param[@"date"];
    NSDateFormatter *g_formatter = [[NSDateFormatter alloc] init];
    [g_formatter setDateFormat:@"yyyy-MM-dd"];
    NSDate *_date = [g_formatter dateFromString:date];
    [_playBackManager quaryRecordInfo:_id date:_date recordPos:nil complete:^(BOOL finish, NSString *message, CRecordInfo *recordInfo) {
        
//        recordInfo.recSegmentList[0].re
        NSMutableArray *ary=[NSMutableArray new];
        for( CRecordSegment *seg in recordInfo.recSegmentList){
            [ary addObject:@{@"start":[self getTime:seg.beginTime],@"end":[self getTime:seg.endTime]}];
         }
        callback(@{@"list":ary});
    } ];
}

-(NSString*)getTime:(TIME_STRUCT)seg{
    NSString *year=[self get10:seg.dwYear];
      NSString *month=[self get10:seg.dwMonth];
      NSString *day=[self get10:seg.dwDay];
      NSString *hour=[self get10:seg.dwHour];
      NSString *min=[self get10:seg.dwMinute];
      NSString *sec=[self get10:seg.dwSecond];
    NSString *y= [[[[[year add:@"-"] add:month]add:@"-"]add:day] add:@" "];
        NSString *d= [[[[hour add:@"-"] add:min]add:@"-"]add:sec] ;
    return [y add:d];
}

-(NSString*)get10:(int)code{
    if(code<10){
        return [@"0" addInt:code];
    }
    return [@"" addInt:code];
}

- (void)updateUITime:(NSTimer *)timer {
    NSTimeInterval osdTime = [_playBackManager getOsdTime];
   NSDate *date= [NSDate dateWithTimeIntervalSince1970:osdTime-8*3600];
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    [formatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
    NSString *dateString = [formatter stringFromDate:date];
    if(_playbackCallback){
        _playbackCallback(@{@"time":dateString},true);
    }
}

-(void)getPlayTime:(WXModuleCallback)callback{
    NSTimeInterval osdTime = [_playBackManager getOsdTime];
    NSDate *date= [NSDate dateWithTimeIntervalSince1970:osdTime-8*3600];
    NSTimeInterval timeInterval = [date timeIntervalSince1970];
    if(callback)
    callback(@{@"time":@(timeInterval)});
    
}


-(void)operate:(NSMutableDictionary*)param{
    NSInteger command=[@"" add: param[@"command"]].intValue;
    BOOL stop= [@"" add: param[@"stop"]].boolValue;
    BOOL end=[@"" add: param[@"end"]].boolValue;
    int speed=5;
    if(param[@"speed"])
    speed=[@"" add: param[@"speed"]].integerValue;
    //    PlayView *player=self.view;
    //     [_realManager stopPtzControl:command withParam1:5];
    //    [player ptzOperation:command stop:stop end:end];
    if (end) {
        [_realManager stopPtzControl:command withParam1:speed];
        
    }else{
        [_realManager startPtzControl:command withParam1:speed];
        
    }
}

-(int)audio:(BOOL)audio{
    
    PlayView *player=self.player;
    player.isAudioing=audio;
    if([player isAudioing]){
        if(self.videoType==0){
            [_realManager openAudio];
            
        }
        else{
            [_playBackManager openAudio];
        }
 
    }else{
        if(    self.videoType==0)
            [_realManager turnoffAudio];
        else{
            [_playBackManager turnoffAudio];
        }
       
    }
}

-(int)enableZoom:(BOOL)zoom{
    PlayView *g_playView=self.player;
    if (g_playView.isEleZooming) {
        
    } else {
        if (g_playView.isPtz) {
            g_playView.isPtz = NO;
        }
        if (g_playView.isChangeQuality) {
            g_playView.isChangeQuality = NO;
        }
    }
    g_playView.isEleZooming = !g_playView.isEleZooming;
    return  g_playView.isEleZooming?1:0;
}

-(void)stop{
    self.playState=PLAY_STATE_STOPED;
    [_realManager stopRealPlay];
}

-(void)pause:(BOOL)pause{
    
    
    self.player.isPausing=pause;
    if(self.player.isPausing){
        if(_playBackManager)
            [_playBackManager pausePlayBack];
    }else{
        if(_playBackManager)
            [_playBackManager resumePlayBack];
    }
    
}


- (void)realPlayCallBack:(PLAY_STATE)playState realManager:(RealPlayManager *)realPlayManager{
     self.playState=playState;
     [self.controller fireEvent:@"playState" params:@{@"state":@(playState)}];
}

#pragma mark  -----程序进入后台和变为活跃时的通知实现
//程序进入后台时,停止预览操作
- (void)stopRealPlay {
    //如果在进行对讲操作,请关闭对讲
    if (self.player.isTalking) {
        dispatch_async(dispatch_get_main_queue(), ^{
            
            self.player.isTalking = NO;
        });
        //停止对讲
        dispatch_async(video_intercom_queue(), ^{
            [self.realManager stopTalking];
        });
    }
    if(self.realManager)
    [self.realManager stopRealPlay];
    if(self.playBackManager){
        if(_playBackManager)
            [_playBackManager pausePlayBack];
//        [self.playBackManager stopPlayBack];
    }
}

- (void)resetRealPlay {
    if (self._id != nil) {
        
        if(self.videoType==0){
            if(self.level==nil){
                self.level=@"high";
            }
            [self realPlay:@{@"id":self._id,@"level":self.level} callback:nil];
        }else{
            if(self.date==nil){
                return;
            }
            if(_playBackManager)
                [_playBackManager resumePlayBack];
//             [self playBack:@{@"id":self._id,@"date":self.date} callback:nil];
        }
       
    }
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
#warning 退出界面必须进行停止播放操作和停止对讲操作,防止因为播放句柄未释放而造成的崩溃
    if (_player.isTalking) {
        dispatch_async(video_intercom_queue(), ^{
            [_realManager stopTalking];
        });
    }
    if(self.controller)
    [self.controller fireEvent:@"viewWillDisappear" params:@{}];
    [_realManager stopRealPlay];
    
}

-(void)viewDidDisappear:(BOOL)animated{
    [super viewDidDisappear:animated];
    if(self.controller)
    [self.controller fireEvent:@"viewDidDisappear" params:@{}];
}
- (void)playBackCallBack:(PLAY_STATE)playState playBackManager:(PlayBackManager *)playBackManager {
    
    switch (playState) {
        case PLAY_STATE_PLAYING: {//正在播放
           
            break;
        }
        case PLAY_STATE_STOPED: {//停止
            
            break;
        }
        case PLAY_STATE_STARTED: {//开始
            NSLog(@"started");
            break;
        }
        case PLAY_STATE_FAILED: {//失败
            
            NSLog(@"failed");
            break;
        }
        case PLAY_STATE_PAUSE: {//暂停
            NSLog(@"pause");
            break;
        }
        case PLAY_STATE_EXCEPTION: {//异常
             
            NSLog(@"exception");
            break;
        }
        default:
            break;
    }
    self.playState=playState;
    [self.controller fireEvent:@"playState" params:@{@"state":@(playState)}];
}



- (void)beginEnterFullScreen{
    CGFloat kScreenWidth= [[UIScreen mainScreen] bounds].size.width;
     CGFloat kScreenHeight= [[UIScreen mainScreen] bounds].size.height;
    [[UIApplication sharedApplication] setStatusBarHidden:true];
    [UIView animateWithDuration:0.2 animations:^{
//        self.player.frame = CGRectMake(0, 0, kScreenHeight, kScreenWidth);
//        self.player.center = CGPointMake(kScreenWidth/2, kScreenHeight/2);
//        CGAffineTransform rotate = CGAffineTransformMakeRotation(M_PI/2);
//
//        self.player.transform = rotate;
        self.parent.frame = CGRectMake(0, 0, kScreenHeight, kScreenWidth);
        self.parent.center = CGPointMake(kScreenWidth/2, kScreenHeight/2);
        CGAffineTransform rotate = CGAffineTransformMakeRotation(M_PI/2);
        
        self.parent.transform = rotate;
        
        
        
        
        
    } completion:^(BOOL finished) {
        
    }];
}

- (void)beginOutFullScreen{
       [[UIApplication sharedApplication] setStatusBarHidden:false];
    CGFloat kScreenWidth= [[UIScreen mainScreen] bounds].size.width;
    CGFloat kScreenHeight= [[UIScreen mainScreen] bounds].size.height;
 
    [UIView animateWithDuration:0.2 animations:^{
//        self.player.transform = CGAffineTransformIdentity;
//        self.player.frame = self.origin_frame;
        self.parent.transform = CGAffineTransformIdentity;
        self.parent.frame = self.origin_frame;
    } completion:^(BOOL finished) {
        
    }];
    
}

- (void)enterFullScreen:(BOOL)isFullScreen{
    _isFullScreen = isFullScreen;
    if(self.origin_frame.size.width==0){
            self.origin_frame=self.parent.frame;
    }
    // 更改全屏按钮状态
//    self.bottomBar.changeScreenBtn.selected = isFullScreen;
//    // 根据是否全屏更改底部工具条的布局
//    self.bottomBar.isFullScreenUpdate = isFullScreen;
    
    // 开始转屏
    if (self.isFullScreen) {
        [self beginEnterFullScreen];
    }else{
        [self beginOutFullScreen];
    }
//    // 告诉使用此videoPlayer的控制器，进而做一些额外的操作，可忽略
//    if ([self.delegate respondsToSelector:@selector(videoPlayer:didChangePlayerFullScreenState:)]) {
//        [self.delegate videoPlayer:self didChangePlayerFullScreenState:self.isFullScreen];
//    }
    
}
- (void)viewWillLayoutSubviews
{
    [self shouldRotateToOrientation:(UIDeviceOrientation)[UIApplication sharedApplication].statusBarOrientation];
}

-(void)shouldRotateToOrientation:(UIDeviceOrientation)orientation {
    
    if (orientation == UIDeviceOrientationPortrait ||orientation == UIDeviceOrientationPortraitUpsideDown) {
        
        // 竖屏
        _isFullScreen = NO;
        _player.frame = _origin_frame;
    }
    
    else {
        
        // 横屏
        CGFloat width = [UIScreen mainScreen].bounds.size.width;
        CGFloat height = [UIScreen mainScreen].bounds.size.height;
        if (width < height)
        {
            CGFloat tmp = width;
            width = height;
            height = tmp;
        }
        _isFullScreen = YES;
        _player.frame = CGRectMake(0, 0, width, height);
    }
}

- (void)changeScreenAction
{
    if ([[UIDevice currentDevice] respondsToSelector:@selector(setOrientation:)])
    {
        NSNumber *num = [[NSNumber alloc] initWithInt:(_isFullScreen?UIInterfaceOrientationLandscapeRight:UIInterfaceOrientationPortrait)];
        [[UIDevice currentDevice] performSelector:@selector(setOrientation:) withObject:(id)num];
        [UIViewController attemptRotationToDeviceOrientation];
    }
    SEL selector=NSSelectorFromString(@"setOrientation:");
    NSInvocation *invocation =[NSInvocation invocationWithMethodSignature:[UIDevice instanceMethodSignatureForSelector:selector]];
    [invocation setSelector:selector];
    [invocation setTarget:[UIDevice currentDevice]];
    int val = _isFullScreen?UIInterfaceOrientationLandscapeRight:UIInterfaceOrientationPortrait;
    [invocation setArgument:&val atIndex:2];
    [invocation invoke];
}
@end
