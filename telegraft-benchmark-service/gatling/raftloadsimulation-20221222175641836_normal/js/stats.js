var stats = {
    type: "GROUP",
name: "All Requests",
path: "",
pathFormatted: "group_missing-name-b06d1",
stats: {
    "name": "All Requests",
    "numberOfRequests": {
        "total": "3624",
        "ok": "3524",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "31",
        "ok": "31",
        "ko": "3016"
    },
    "maxResponseTime": {
        "total": "3570",
        "ok": "1658",
        "ko": "3570"
    },
    "meanResponseTime": {
        "total": "526",
        "ok": "454",
        "ko": "3086"
    },
    "standardDeviation": {
        "total": "510",
        "ok": "276",
        "ko": "132"
    },
    "percentiles1": {
        "total": "436",
        "ok": "423",
        "ko": "3037"
    },
    "percentiles2": {
        "total": "643",
        "ok": "625",
        "ko": "3075"
    },
    "percentiles3": {
        "total": "1088",
        "ok": "972",
        "ko": "3501"
    },
    "percentiles4": {
        "total": "3050",
        "ok": "1226",
        "ko": "3544"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 3148,
    "percentage": 87
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 335,
    "percentage": 9
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 41,
    "percentage": 1
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 3
},
    "meanNumberOfRequestsPerSecond": {
        "total": "97.946",
        "ok": "95.243",
        "ko": "2.703"
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
        "total": "1812",
        "ok": "1812",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "31",
        "ok": "31",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "1330",
        "ok": "1330",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "299",
        "ok": "299",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "201",
        "ok": "201",
        "ko": "-"
    },
    "percentiles1": {
        "total": "242",
        "ok": "242",
        "ko": "-"
    },
    "percentiles2": {
        "total": "380",
        "ok": "380",
        "ko": "-"
    },
    "percentiles3": {
        "total": "708",
        "ok": "708",
        "ko": "-"
    },
    "percentiles4": {
        "total": "1087",
        "ok": "1087",
        "ko": "-"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 1760,
    "percentage": 97
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 46,
    "percentage": 3
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 6,
    "percentage": 0
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 0,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "48.973",
        "ok": "48.973",
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
        "total": "1812",
        "ok": "1712",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "45",
        "ok": "45",
        "ko": "3016"
    },
    "maxResponseTime": {
        "total": "3570",
        "ok": "1658",
        "ko": "3570"
    },
    "meanResponseTime": {
        "total": "754",
        "ok": "617",
        "ko": "3086"
    },
    "standardDeviation": {
        "total": "614",
        "ok": "248",
        "ko": "132"
    },
    "percentiles1": {
        "total": "605",
        "ok": "589",
        "ko": "3037"
    },
    "percentiles2": {
        "total": "785",
        "ok": "747",
        "ko": "3075"
    },
    "percentiles3": {
        "total": "3021",
        "ok": "1047",
        "ko": "3501"
    },
    "percentiles4": {
        "total": "3085",
        "ok": "1376",
        "ko": "3544"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 1388,
    "percentage": 77
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 289,
    "percentage": 16
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 35,
    "percentage": 2
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 6
},
    "meanNumberOfRequestsPerSecond": {
        "total": "48.973",
        "ok": "46.27",
        "ko": "2.703"
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
