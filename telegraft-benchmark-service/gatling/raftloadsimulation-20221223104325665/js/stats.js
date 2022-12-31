var stats = {
    type: "GROUP",
name: "All Requests",
path: "",
pathFormatted: "group_missing-name-b06d1",
stats: {
    "name": "All Requests",
    "numberOfRequests": {
        "total": "524",
        "ok": "424",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "29",
        "ok": "29",
        "ko": "3015"
    },
    "maxResponseTime": {
        "total": "3467",
        "ok": "3467",
        "ko": "3381"
    },
    "meanResponseTime": {
        "total": "1430",
        "ok": "1047",
        "ko": "3053"
    },
    "standardDeviation": {
        "total": "1111",
        "ok": "870",
        "ko": "46"
    },
    "percentiles1": {
        "total": "1112",
        "ok": "731",
        "ko": "3041"
    },
    "percentiles2": {
        "total": "2588",
        "ok": "1676",
        "ko": "3058"
    },
    "percentiles3": {
        "total": "3061",
        "ok": "2733",
        "ko": "3120"
    },
    "percentiles4": {
        "total": "3213",
        "ok": "3034",
        "ko": "3231"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 230,
    "percentage": 44
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 42,
    "percentage": 8
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 152,
    "percentage": 29
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 19
},
    "meanNumberOfRequestsPerSecond": {
        "total": "34.933",
        "ok": "28.267",
        "ko": "6.667"
    }
},
contents: {
"req_user-reads-mess-c5147": {
        type: "REQUEST",
        name: "user_reads_messages",
path: "user_reads_messages",
pathFormatted: "req_user-reads-mess-c5147",
stats: {
    "name": "user_reads_messages",
    "numberOfRequests": {
        "total": "262",
        "ok": "262",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "29",
        "ok": "29",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "3467",
        "ok": "3467",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "722",
        "ok": "722",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "720",
        "ok": "720",
        "ko": "-"
    },
    "percentiles1": {
        "total": "488",
        "ok": "488",
        "ko": "-"
    },
    "percentiles2": {
        "total": "948",
        "ok": "948",
        "ko": "-"
    },
    "percentiles3": {
        "total": "2365",
        "ok": "2365",
        "ko": "-"
    },
    "percentiles4": {
        "total": "3393",
        "ok": "3393",
        "ko": "-"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 185,
    "percentage": 71
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 32,
    "percentage": 12
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 45,
    "percentage": 17
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 0,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "17.467",
        "ok": "17.467",
        "ko": "-"
    }
}
    },"req_user-sends-mess-426e9": {
        type: "REQUEST",
        name: "user_sends_messages",
path: "user_sends_messages",
pathFormatted: "req_user-sends-mess-426e9",
stats: {
    "name": "user_sends_messages",
    "numberOfRequests": {
        "total": "262",
        "ok": "162",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "118",
        "ok": "118",
        "ko": "3015"
    },
    "maxResponseTime": {
        "total": "3381",
        "ok": "3044",
        "ko": "3381"
    },
    "meanResponseTime": {
        "total": "2138",
        "ok": "1574",
        "ko": "3053"
    },
    "standardDeviation": {
        "total": "973",
        "ok": "833",
        "ko": "46"
    },
    "percentiles1": {
        "total": "2437",
        "ok": "1648",
        "ko": "3041"
    },
    "percentiles2": {
        "total": "3035",
        "ok": "2300",
        "ko": "3058"
    },
    "percentiles3": {
        "total": "3080",
        "ok": "2796",
        "ko": "3120"
    },
    "percentiles4": {
        "total": "3139",
        "ok": "2999",
        "ko": "3231"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 45,
    "percentage": 17
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 10,
    "percentage": 4
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 107,
    "percentage": 41
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 38
},
    "meanNumberOfRequestsPerSecond": {
        "total": "17.467",
        "ok": "10.8",
        "ko": "6.667"
    }
}
    }
}

}

function fillStats(stat){
    $("#numberOfRequests").append(stat.numberOfRequests.total);
    $("#numberOfRequestsOK").append(stat.numberOfRequests.ok);
    $("#numberOfRequestsKO").append(stat.numberOfRequests.ko);

    $("#minResponseTime").append(stat.minResponseTime.total);
    $("#minResponseTimeOK").append(stat.minResponseTime.ok);
    $("#minResponseTimeKO").append(stat.minResponseTime.ko);

    $("#maxResponseTime").append(stat.maxResponseTime.total);
    $("#maxResponseTimeOK").append(stat.maxResponseTime.ok);
    $("#maxResponseTimeKO").append(stat.maxResponseTime.ko);

    $("#meanResponseTime").append(stat.meanResponseTime.total);
    $("#meanResponseTimeOK").append(stat.meanResponseTime.ok);
    $("#meanResponseTimeKO").append(stat.meanResponseTime.ko);

    $("#standardDeviation").append(stat.standardDeviation.total);
    $("#standardDeviationOK").append(stat.standardDeviation.ok);
    $("#standardDeviationKO").append(stat.standardDeviation.ko);

    $("#percentiles1").append(stat.percentiles1.total);
    $("#percentiles1OK").append(stat.percentiles1.ok);
    $("#percentiles1KO").append(stat.percentiles1.ko);

    $("#percentiles2").append(stat.percentiles2.total);
    $("#percentiles2OK").append(stat.percentiles2.ok);
    $("#percentiles2KO").append(stat.percentiles2.ko);

    $("#percentiles3").append(stat.percentiles3.total);
    $("#percentiles3OK").append(stat.percentiles3.ok);
    $("#percentiles3KO").append(stat.percentiles3.ko);

    $("#percentiles4").append(stat.percentiles4.total);
    $("#percentiles4OK").append(stat.percentiles4.ok);
    $("#percentiles4KO").append(stat.percentiles4.ko);

    $("#meanNumberOfRequestsPerSecond").append(stat.meanNumberOfRequestsPerSecond.total);
    $("#meanNumberOfRequestsPerSecondOK").append(stat.meanNumberOfRequestsPerSecond.ok);
    $("#meanNumberOfRequestsPerSecondKO").append(stat.meanNumberOfRequestsPerSecond.ko);
}
