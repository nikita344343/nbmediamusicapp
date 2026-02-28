<?php
/**
 * Plugin Name: NB App REST API
 * Description: REST API для мобильного приложения: каталог релизов и статистика (по пользователю). Работает с NB Music Catalog и Track Stats Plugin.
 * Version: 1.0
 * Author: NB Media
 */

defined( 'ABSPATH' ) || exit;

class NB_App_REST_API {

    public static function init() {
        add_action( 'rest_api_init', [ __CLASS__, 'register_routes' ] );
    }

    public static function register_routes() {
        register_rest_route( 'nb-app/v1', '/catalog', [
            'methods'             => WP_REST_Server::READABLE,
            'callback'            => [ __CLASS__, 'get_catalog' ],
            'permission_callback' => function () {
                return is_user_logged_in();
            },
        ] );

        register_rest_route( 'nb-app/v1', '/stats', [
            'methods'             => WP_REST_Server::READABLE,
            'callback'            => [ __CLASS__, 'get_stats' ],
            'permission_callback' => function () {
                return is_user_logged_in();
            },
            'args' => [
                'date_from' => [ 'type' => 'string', 'required' => false ],
                'date_to'   => [ 'type' => 'string', 'required' => false ],
            ],
        ] );
    }

    private static function get_priority_meta( $post_id, $keys ) {
        foreach ( $keys as $key ) {
            $val = get_post_meta( $post_id, $key, true );
            if ( ! empty( $val ) && $val !== '—' ) {
                return $val;
            }
        }
        return '';
    }

    private static function get_accumulated_stats( $post_id, $upc_raw, $tracks_array ) {
        global $wpdb;
        $table = $wpdb->prefix . 'track_stats';
        if ( $wpdb->get_var( "SHOW TABLES LIKE '$table'" ) !== $table ) {
            return [ 'total' => 0, 'platforms' => [] ];
        }

        $user_id = get_post_field( 'post_author', $post_id );
        $clean_upc = preg_replace( '/[^a-zA-Z0-9]/', '', (string) $upc_raw );
        $where = [ 'user_id = ' . intval( $user_id ) ];
        if ( $clean_upc ) {
            $where[] = "ean = '" . esc_sql( $clean_upc ) . "'";
        }
        $sql = "SELECT track_name, track_version, plays, platform FROM $table WHERE (" . implode( ' OR ', $where ) . ")";
        $results = $wpdb->get_results( $sql );
        $total = 0;
        $platforms = [];
        if ( $results ) {
            foreach ( $results as $row ) {
                $plays = intval( $row->plays );
                $total += $plays;
                $plat = trim( $row->platform ) ?: 'Other';
                if ( ! isset( $platforms[ $plat ] ) ) {
                    $platforms[ $plat ] = 0;
                }
                $platforms[ $plat ] += $plays;
            }
        }
        arsort( $platforms );
        return [ 'total' => $total, 'platforms' => $platforms ];
    }

    private static function get_track_status( $pid, $date_str = '' ) {
        if ( get_post_meta( $pid, '_nb_is_deleted', true ) === '1' ) {
            return [ 'text' => 'Удален', 'class' => 'deleted' ];
        }
        $s = get_post_status( $pid );
        if ( $s === 'trash' ) {
            return [ 'text' => 'Удален', 'class' => 'deleted' ];
        }
        if ( $s === 'draft' ) {
            return [ 'text' => 'Черновик', 'class' => 'draft' ];
        }
        if ( $s === 'publish' ) {
            if ( ! empty( $date_str ) ) {
                $dt = DateTime::createFromFormat( 'd.m.Y', $date_str );
                if ( $dt && $dt->getTimestamp() <= current_time( 'timestamp' ) ) {
                    return [ 'text' => 'Выпущен', 'class' => 'released' ];
                }
            }
            return [ 'text' => 'Одобрен', 'class' => 'approved' ];
        }
        return [ 'text' => 'Модерация', 'class' => 'pending' ];
    }

    public static function get_catalog( WP_REST_Request $request ) {
        $user_id = get_current_user_id();
        if ( ! $user_id ) {
            return new WP_REST_Response( [ 'error' => 'Unauthorized' ], 401 );
        }

        $posts = get_posts( [
            'post_type'      => 'post',
            'post_status'    => [ 'publish', 'pending', 'draft', 'future' ],
            'author'         => $user_id,
            'posts_per_page' => -1,
            'orderby'        => 'date',
            'order'          => 'DESC',
        ] );

        $items = [];
        foreach ( $posts as $post ) {
            $pid = $post->ID;
            $upc = self::get_priority_meta( $pid, [ 'upc_code', 'upc_video', 'nb_upc' ] );
            $date = self::get_priority_meta( $pid, [ 'date_relise', 'release_date' ] ) ?: get_the_date( 'd.m.Y', $pid );
            $title = self::get_priority_meta( $pid, [ 'realise_namee', 'release_name' ] ) ?: $post->post_title;
            $artist = self::get_priority_meta( $pid, [ 'artist_nameee', 'artist_name' ] );
            if ( empty( $artist ) ) {
                $artist_info = get_post_meta( $pid, 'artist_info', true );
                if ( is_array( $artist_info ) && ! empty( $artist_info ) ) {
                    $first = reset( $artist_info );
                    $artist = isset( $first['artist'] ) ? $first['artist'] : ( isset( $first[1] ) ? $first[1] : '' );
                }
            }
            if ( empty( $artist ) ) {
                $artist = get_the_author_meta( 'display_name', $post->post_author );
            }
            $genre = get_post_meta( $pid, 'genre', true ) ?: 'Pop';
            $artist_info_raw = get_post_meta( $pid, 'artist_info', true );
            $tracks = is_array( $artist_info_raw ) ? $artist_info_raw : ( is_string( $artist_info_raw ) ? ( json_decode( $artist_info_raw, true ) ?: [] ) : [] );
            $stats = self::get_accumulated_stats( $pid, $upc, $tracks );
            $status = self::get_track_status( $pid, $date );
            $cover = get_the_post_thumbnail_url( $pid, 'medium' ) ?: get_the_post_thumbnail_url( $pid, 'full' );

            $items[] = [
                'id'          => $pid,
                'title'       => $title,
                'artist'      => $artist,
                'date'        => $date,
                'upc'         => $upc,
                'genre'       => $genre,
                'status'      => $status['text'],
                'status_class'=> $status['class'],
                'cover_url'   => $cover,
                'total_plays' => $stats['total'],
                'platforms'   => $stats['platforms'],
                'tracks'      => array_values( array_map( function ( $t ) {
                    $t = (array) $t;
                    return [
                        'track'   => $t['track'] ?? $t[0] ?? '',
                        'artist'  => $t['artist'] ?? $t[1] ?? '',
                        'version' => $t['version'] ?? '',
                        'isrc'    => $t['isrc'] ?? '',
                    ];
                }, $tracks ) ),
            ];
        }

        return rest_ensure_response( [ 'items' => $items ] );
    }

    public static function get_stats( WP_REST_Request $request ) {
        $user_id = get_current_user_id();
        if ( ! $user_id ) {
            return new WP_REST_Response( [ 'error' => 'Unauthorized' ], 401 );
        }

        global $wpdb;
        $table = $wpdb->prefix . 'track_stats';
        if ( $wpdb->get_var( "SHOW TABLES LIKE '$table'" ) !== $table ) {
            return rest_ensure_response( [
                'total_plays' => 0,
                'platforms'   => [],
                'tracks'      => [],
            ] );
        }

        $date_from = $request->get_param( 'date_from' );
        $date_to   = $request->get_param( 'date_to' );
        $where = [ 'user_id = ' . intval( $user_id ) ];
        $args = [];
        if ( $date_from && $date_to ) {
            $where[] = 'COALESCE(import_date, DATE(created_at)) BETWEEN %s AND %s';
            $args[] = $date_from;
            $args[] = $date_to;
        }
        $sql = "SELECT track_name, track_version, plays, platform FROM $table WHERE " . implode( ' AND ', $where );
        if ( $args ) {
            $sql = $wpdb->prepare( $sql, $args );
        }
        $sql .= ' ORDER BY created_at ASC';
        $results = $wpdb->get_results( $sql );

        $total_plays = 0;
        $platforms = [];
        $by_track = [];
        foreach ( $results as $row ) {
            $plays = intval( $row->plays );
            $total_plays += $plays;
            $plat = trim( $row->platform ) ?: 'Other';
            if ( ! isset( $platforms[ $plat ] ) ) {
                $platforms[ $plat ] = 0;
            }
            $platforms[ $plat ] += $plays;
            $key = $row->track_name . '|' . ( $row->track_version ?? '' );
            if ( ! isset( $by_track[ $key ] ) ) {
                $by_track[ $key ] = [ 'name' => $row->track_name, 'version' => $row->track_version ?? '', 'plays' => 0 ];
            }
            $by_track[ $key ]['plays'] += $plays;
        }
        arsort( $platforms );
        uasort( $by_track, function ( $a, $b ) {
            return $b['plays'] <=> $a['plays'];
        } );

        return rest_ensure_response( [
            'total_plays' => $total_plays,
            'platforms'  => $platforms,
            'tracks'     => array_values( array_slice( $by_track, 0, 50 ) ),
        ] );
    }
}

NB_App_REST_API::init();
