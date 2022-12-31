var stats = {
    type: "GROUP",
name: "All Requests",
path: "",
pathFormatted: "group_missing-name-b06d1",
stats: {
    "name": "All Requests",
    "numberOfRequests": {
        "total": "750",
        "ok": "650",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "26",
        "ok": "26",
        "ko": "3015"
    },
    "maxResponseTime": {
        "total": "3348",
        "ok": "2906",
        "ko": "3348"
    },
    "meanResponseTime": {
        "total": "952",
        "ok": "630",
        "ko": "3048"
    },
    "standardDeviation": {
        "total": "997",
        "ok": "606",
        "ko": "38"
    },
    "percentiles1": {
        "total": "433",
        "ok": "361",
        "ko": "3042"
    },
    "percentiles2": {
        "total": "1458",
        "ok": "790",
        "ko": "3056"
    },
    "percentiles3": {
        "total": "3049",
        "ok": "1995",
        "ko": "3086"
    },
    "percentiles4": {
        "total": "3081",
        "ok": "2419",
        "ko": "3164"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 489,
    "percentage": 65
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 47,
    "percentage": 6
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 114,
    "percentage": 15
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 13
},
    "meanNumberOfRequestsPerSecond": {
        "total": "34.091",
        "ok": "29.545",
        "ko": "4.545"
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
        "total": "375",
        "ok": "375",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "26",
        "ok": "26",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "1378",
        "ok": "1378",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "327",
        "ok": "327",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "214",
        "ok": "214",
        "ko": "-"
    },
    "percentiles1": {
        "total": "271",
        "ok": "271",
        "ko": "-"
    },
    "percentiles2": {
        "total": "396",
        "ok": "396",
        "ko": "-"
    },
    "percentiles3": {
        "total": "742",
        "ok": "742",
        "ko": "-"
    },
    "percentiles4": {
        "total": "1187",
        "ok": "1187",
        "ko": "-"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 362,
    "percentage": 97
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 9,
    "percentage": 2
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 4,
    "percentage": 1
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 0,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "17.045",
        "ok": "17.045",
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
        "total": "375",
        "ok": "275",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "82",
        "ok": "82",
        "ko": "3015"
    },
    "maxResponseTime": {
        "total": "3348",
        "ok": "2906",
        "ko": "3348"
    },
    "meanResponseTime": {
        "total": "1578",
        "ok": "1043",
        "ko": "3048"
    },
    "standardDeviation": {
        "total": "1077",
        "ok": "713",
        "ko": "38"
    },
    "percentiles1": {
        "total": "1458",
        "ok": "899",
        "ko": "3042"
    },
    "percentiles2": {
        "total": "3020",
        "ok": "1678",
        "ko": "3056"
    },
    "percentiles3": {
        "total": "3061",
        "ok": "2300",
        "ko": "3086"
    },
    "percentiles4": {
        "total": "3089",
        "ok": "2555",
        "ko": "3164"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 127,
    "percentage": 34
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 38,
    "percentage": 10
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 110,
    "percentage": 29
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 27
},
    "meanNumberOfRequestsPerSecond": {
        "total": "17.045",
        "ok": "12.5",
        "ko": "4.545"
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
