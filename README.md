# transportation_mode_learning_framework

## Debugging of predicted mode based on accelerometer data

main class: `eu.qrowd_project.wp6.transportation_mode_learning.Predict`

prerequisites: [the R project for the ML part](https://github.com/QROWD/TR), i.e.
```bash 
git clone git@github.com:QROWD/TR.git
```

usage: `eu.qrowd_project.wp6.transportation_mode_learning.Predict <$PATH_TO_R_PROJECT> <$PATH_TO_GPS_CSV> <$PATH_TO_ACCELEROMETER_CSV>`

output: GeoJson lines and GPS points located at `/tmp/trip{$id}_lines_with_points.json`, e.g.

```json
{
      "type": "Feature",
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [
            11.10949,
            46.08808
          ],
          [
            11.10988246565158,
            46.088481810324325
          ]
        ]
      },
      "properties": {
        "timestamp-start": "2018-04-09 13:38:32.0",
        "timestamp-end": "2018-04-09 13:39:30.0",
        "stroke": "red",
        "mode": "\"bike\""
      }
    },
```
The `properties` include the mode of transportation as string value.

Color Mapping:

| mode  | color  |
|-------|--------|
| bike  | red    |
| bus   | green  |
| car   | blue   |
| still | yellow |
| train | olive  |
| walk  | purple |

