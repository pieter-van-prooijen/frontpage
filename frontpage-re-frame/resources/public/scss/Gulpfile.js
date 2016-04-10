var gulp = require('gulp');
var sass = require('gulp-sass');
var sourcemaps = require('gulp-sourcemaps');


gulp.task('sass', function () {
    return gulp
        .src(["frontpage.scss"])
        .pipe(sourcemaps.init())
        .pipe(sass({includePaths: ["bower_components/foundation-sites/scss"]}).on('error', sass.logError))
        .pipe(sourcemaps.write('.'))
        .pipe(gulp.dest('../css/'));
});

gulp.task('sass:watch', ['sass'], function () {
    gulp.watch('*.scss', ['sass']);
});
