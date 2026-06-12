//
//  Pedometer.m
//  Copyright (c) 2014 Lee Crossley - http://ilee.co.uk
//

#import "Cordova/CDV.h"
#import "Cordova/CDVViewController.h"
#import "CoreMotion/CoreMotion.h"
#import "Stepper.h"

@interface Stepper ()
    @property (nonatomic, strong) CMPedometer *pedometer;
@end

@implementation Stepper

- (CMPedometer*) pedometer {
    if (_pedometer == nil) {
        _pedometer = [[CMPedometer alloc] init];
    }
    return _pedometer;
}

- (void) isStepCountingAvailable:(CDVInvokedUrlCommand*)command;
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:[CMPedometer isStepCountingAvailable]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) isDistanceAvailable:(CDVInvokedUrlCommand*)command;
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:[CMPedometer isDistanceAvailable]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) isFloorCountingAvailable:(CDVInvokedUrlCommand*)command;
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:[CMPedometer isFloorCountingAvailable]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) startStepperUpdates:(CDVInvokedUrlCommand*)command;
{
    __block CDVPluginResult* pluginResult = nil;

    NSDictionary *options = [command.arguments objectAtIndex:0];
    NSString *timeZone = [options objectForKey:@"timeZone"];
    NSCalendar *calendar = [NSCalendar currentCalendar];
    if (timeZone && ![timeZone isKindOfClass:[NSNull class]] && [NSTimeZone timeZoneWithName:timeZone]) {
        calendar.timeZone = [NSTimeZone timeZoneWithName:timeZone];
    }
    NSDateComponents *dateComponents = [calendar components:NSCalendarUnitYear | NSCalendarUnitMonth | NSCalendarUnitDay fromDate:[NSDate date]];
    NSDate *startDate = [calendar dateFromComponents:dateComponents];
    
    [self.pedometer startPedometerUpdatesFromDate:startDate withHandler:^(CMPedometerData *pedometerData, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (error)
            {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]];
            }
            else
            {
                NSDictionary* pedestrianData = @{
                    @"startDate": [NSString stringWithFormat:@"%f", [pedometerData.startDate timeIntervalSince1970] * 1000],
                    @"endDate": [NSString stringWithFormat:@"%f", [pedometerData.endDate timeIntervalSince1970] * 1000],
                    @"steps_today": [CMPedometer isStepCountingAvailable] && pedometerData.numberOfSteps ? pedometerData.numberOfSteps : [NSNumber numberWithInt:0],
                    @"distance": [CMPedometer isDistanceAvailable] && pedometerData.distance ? pedometerData.distance : [NSNumber numberWithInt:0],
                    @"floorsAscended": [CMPedometer isFloorCountingAvailable] && pedometerData.floorsAscended ? pedometerData.floorsAscended : [NSNumber numberWithInt:0],
                    @"floorsDescended": [CMPedometer isFloorCountingAvailable] && pedometerData.floorsDescended ? pedometerData.floorsDescended : [NSNumber numberWithInt:0]
                };
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:pedestrianData];
                [pluginResult setKeepCallbackAsBool:true];
            }

            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        });
    }];
}

- (void) stopStepperUpdates:(CDVInvokedUrlCommand*)command;
{
    [self.pedometer stopPedometerUpdates];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) getStepsByPeriod:(CDVInvokedUrlCommand*)command;
{
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    // A fixed-format formatter must be locale/calendar independent. Without this, dateFromString
    // returns nil on devices set to 12-hour time or a non-Gregorian calendar, and the nil date
    // makes CMPedometer throw "Invalid parameter not satisfying: start", crashing the app.
    dateFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    dateFormatter.calendar = [[NSCalendar alloc] initWithCalendarIdentifier:NSCalendarIdentifierGregorian];
    [dateFormatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"];
    [dateFormatter setTimeZone:[NSTimeZone timeZoneForSecondsFromGMT:0]];

    NSDate* startDate = [dateFormatter dateFromString:[command.arguments objectAtIndex:0]];
    NSDate* endDate = [dateFormatter dateFromString:[command.arguments objectAtIndex:1]];

    __block CDVPluginResult* pluginResult = nil;

    if (startDate == nil) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid start date"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    // CMPedometer rejects dates in the future; clamp the end of the range to now.
    NSDate* now = [NSDate date];
    if (endDate == nil || [endDate compare:now] == NSOrderedDescending) {
        endDate = now;
    }
    if ([startDate compare:endDate] == NSOrderedDescending) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid date range"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    [self.pedometer queryPedometerDataFromDate:startDate toDate:endDate withHandler:^(CMPedometerData *pedometerData, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (error)
            {
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]];
            }
            else
            {
                NSDictionary* pedestrianData = @{
                    @"steps": [CMPedometer isStepCountingAvailable] && pedometerData.numberOfSteps ? pedometerData.numberOfSteps : [NSNumber numberWithInt:0],
                    @"distance": [CMPedometer isDistanceAvailable] && pedometerData.distance ? pedometerData.distance : [NSNumber numberWithInt:0],
                    @"floorsAscended": [CMPedometer isFloorCountingAvailable] && pedometerData.floorsAscended ? pedometerData.floorsAscended : [NSNumber numberWithInt:0],
                    @"floorsDescended": [CMPedometer isFloorCountingAvailable] && pedometerData.floorsDescended ? pedometerData.floorsDescended : [NSNumber numberWithInt:0]
                };
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:pedestrianData];
            }

            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        });
    }];
}

- (void)getLastEntries:(CDVInvokedUrlCommand*)command {
    NSNumber *numberOfEntries = [command.arguments objectAtIndex:0];

    if (![numberOfEntries isKindOfClass:[NSNumber class]]) {
       CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid argument. Please provide a valid number of entries."];
       [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
       return;
    }

    NSInteger x = [numberOfEntries integerValue];
    if (x <= 0) {
       CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid argument. Please provide a valid number of entries."];
       [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
       return;
    }

    NSCalendar *calendar = [NSCalendar currentCalendar];

    // Start at midnight today, then go back (x - 1) days so the loop covers the last x days up to today.
    NSDateComponents *todayComponents = [calendar components:(NSCalendarUnitYear | NSCalendarUnitMonth | NSCalendarUnitDay) fromDate:[NSDate date]];
    NSDate *startOfToday = [calendar dateFromComponents:todayComponents];
    NSDateComponents *offset = [[NSDateComponents alloc] init];
    offset.day = -(x - 1);
    __block NSDate *startDate = [calendar dateByAddingComponents:offset toDate:startOfToday options:0];

    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    dateFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    dateFormatter.calendar = [[NSCalendar alloc] initWithCalendarIdentifier:NSCalendarIdentifierGregorian];
    [dateFormatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];

    NSMutableArray *entriesArray = [NSMutableArray array];
    __block NSInteger completed = 0;

    for (NSInteger i = 0; i < x; i++) {
        NSDate *dayStart = startDate;
        NSDate *dayEnd = [calendar dateByAddingUnit:NSCalendarUnitDay value:1 toDate:dayStart options:0];
        [self.pedometer queryPedometerDataFromDate:dayStart toDate:dayEnd withHandler:^(CMPedometerData *pedometerData, NSError *error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (!error && pedometerData) {
                    NSDictionary *pedestrianData = @{
                        @"data": [dateFormatter stringFromDate:pedometerData.startDate],
                        @"steps": [CMPedometer isStepCountingAvailable] && pedometerData.numberOfSteps ? pedometerData.numberOfSteps : [NSNumber numberWithInt:0],
                        @"distance": [CMPedometer isDistanceAvailable] && pedometerData.distance ? pedometerData.distance : [NSNumber numberWithInt:0]
                    };
                    [entriesArray addObject:pedestrianData];
                }
                // Resolve once every day query has returned, regardless of callback order.
                completed++;
                if (completed == x) {
                    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{@"entries": entriesArray}];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
            });
        }];
        startDate = dayEnd;
    }
}

@end
